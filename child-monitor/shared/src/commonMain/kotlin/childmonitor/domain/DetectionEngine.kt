package childmonitor.domain

import childmonitor.data.database.entity.*
import childmonitor.model.*
import kotlin.math.abs

/** 按真实采集时间推进证据，未知区间截断连续性，不把未检出人体等同于离座。 */
class DetectionEngine(private val sessionId: String, private val newId: () -> String) {
    data class Facts(val events: List<EventEntity>, val coverage: List<CoverageEntity>, val rest: List<RestWindowEntity>)
    data class Feedback(val seated: Boolean, val away: Boolean, val unclear: Boolean,
        val needsGuardian: Boolean, val activeKinds: Set<EventKind>, val calibration: Calibration?)
    private data class Feature(val headHeight: Float?, val headAngle: Float?, val bodyAngle: Float?, val shoulderWidth: Float, val center: Point)
    private data class Candidate(var startUs: Long? = null, var recoveryUs: Long? = null, var event: EventEntity? = null)
    var calibration: Calibration? = null
        private set
    private var region: SeatRegion? = null
    private val seatVisibility = SeatVisibilityTracker()
    val emptySeatReady: Boolean get() = seatVisibility.ready
    var placementIssue = "region"
        private set
    val placementProgress: Float get() = if (!emptySeatReady) seatVisibility.progress else
        baseline.firstOrNull()?.let { (baseline.last().first - it.first).toFloat() / (DefaultMonitorConfig.calibrationStableMs * 1_000) } ?: 0f
    private val baseline = mutableListOf<Pair<Long, Feature>>()
    private val candidates = mutableMapOf<EventKind, Candidate>()
    private val events = linkedMapOf<String, EventEntity>()
    private val coverage = linkedMapOf<String, CoverageEntity>()
    private val openCoverage = mutableMapOf<String, String>()
    private val rests = linkedMapOf<String, RestWindowEntity>()
    private var lastUs: Long? = null
    private var lastTarget: Point? = null
    private var seatState = "uninitialized"
    private var seatCandidateUs: Long? = null
    private var awayId: String? = null
    private var activeAway: EventEntity? = null
    private var guardian = false
    private var calibrationVersion = 0L
    private var restDeadlineUs: Long? = null
    private var restUntilUs: Long? = null
    private var uncertain: EventEntity? = null
    var seatedSinceUs: Long? = null
        private set
    var lastFeedback = Feedback(false, false, true, false, emptySet(), null)
        private set

    fun confirmRegion(value: SeatRegion) {
        value.validate()
        region = value
        placementIssue = "empty"
        seatVisibility.reset()
        baseline.clear()
        guardian = false
    }
    fun reposition(atUs: Long) {
        close(atUs, "unknown")
        calibration = null
        region = null
        placementIssue = "region"
        seatVisibility.reset()
        awayId = null
        activeAway = null
        lastTarget = null
        lastUs = null
        guardian = false
        baseline.clear()
        seatState = "uninitialized"
        seatCandidateUs = null
        seatedSinceUs = null
        restDeadlineUs = null
        restUntilUs = null
        feedback(false, false, true)
    }
    fun allowRest(startedUs: Long) { restDeadlineUs = startedUs + DefaultMonitorConfig.responseWindowMs * 1_000 }

    fun accept(observation: Observation, settings: MonitorSettings, revisionId: String): Feedback {
        val time = observation.frameTimeUs
        val prior = lastUs
        if (prior != null && time <= prior) return lastFeedback
        if (prior != null && time - prior > DefaultMonitorConfig.evidenceMaxGapMs * 1_000) close(prior, "unknown")
        lastUs = time
        val feature = features(observation)
        val roi = region
        val modelAmbiguous = observation.people.size > 1
        placementIssue = when {
            roi == null -> "region"
            observation.deviceMoved || guardian -> "moved"
            modelAmbiguous -> "multiple"
            !observation.sceneClear -> "lighting"
            !emptySeatReady -> if (observation.people.any { it.left < roi.right && it.right > roi.left && it.top < roi.bottom && it.bottom > roi.top }) "occupied" else "empty"
            feature == null -> "shoulders"
            !roi.contains(feature.center) -> "outside"
            feature.headAngle == null || feature.headHeight == null -> "head"
            abs(feature.headAngle) > .3f || feature.headHeight < .15f || feature.bodyAngle?.let { abs(it) > .4f } == true -> "upright"
            else -> "stable"
        }
        if (modelAmbiguous || observation.deviceMoved || calibration?.transformVersion?.let { it != observation.transformVersion } == true) guardian = true
        if (guardian || !observation.sceneClear || modelAmbiguous) return unknown(time, revisionId)
        val clearEmptySeat = roi?.let { seatVisibility.observe(observation, it) } == true
        if (calibration == null) {
            updateCoverage(setOf("prepare"), time, revisionId)
            if (!seatVisibility.ready || roi == null || feature == null || observation.people.size != 1 || !roi.contains(feature.center) ||
                feature.headAngle == null || feature.headHeight == null || abs(feature.headAngle) > .3f || feature.headHeight < .15f ||
                feature.bodyAngle?.let { abs(it) > .4f } == true) {
                baseline.clear()
            } else {
                baseline += time to feature
                val first = baseline.first()
                if (time - first.first >= DefaultMonitorConfig.calibrationStableMs * 1_000) {
                    fun median(values: List<Float>) = values.sorted()[values.size / 2]
                    val heights = baseline.mapNotNull { it.second.headHeight }
                    val widths = baseline.map { it.second.shoulderWidth }
                    val stable = heights.all { abs(it - median(heights)) < .15f } && widths.all { abs(it - median(widths)) < .05f }
                    if (stable) {
                        calibrationVersion++
                        calibration = Calibration(roi, median(heights), median(baseline.mapNotNull { it.second.headAngle }),
                            baseline.mapNotNull { it.second.bodyAngle }.let { if (it.size == baseline.size) median(it) else null },
                            median(widths), observation.transformVersion, calibrationVersion)
                        seatState = "seated"
                        seatedSinceUs = time
                    } else baseline.clear()
                }
            }
            return feedback(false, false, true)
        }
        val current = checkNotNull(calibration)
        val inSeat = feature != null && observation.people.size == 1 && current.roi.contains(feature.center)
        val outside = feature != null && observation.people.size == 1 && !current.roi.contains(feature.center)
        val trackedDeparture = outside && lastTarget?.let { abs(feature!!.center.x - it.x) < .25f && abs(feature.center.y - it.y) < .25f } == true
        if (feature != null) lastTarget = feature.center
        if (inSeat) {
            if (seatState != "seated") {
                if (seatState != "returning") { seatState = "returning"; seatCandidateUs = time }
                if (time - checkNotNull(seatCandidateUs) < DefaultMonitorConfig.recoveryMs * 1_000) return unknown(time, revisionId, resetCandidate = false)
                activeAway?.let { events[it.id] = it.copy(endUs = checkNotNull(seatCandidateUs), endReason = "recovered") }
                if (awayId != null) {
                    val id = newId()
                    events[id] = EventEntity(id, sessionId, EventKind.Return.name, checkNotNull(seatCandidateUs), checkNotNull(seatCandidateUs), time, id, "recovered", revisionId)
                }
                activeAway = null
                awayId = null
                restUntilUs = null
                seatState = "seated"
                seatedSinceUs = time
            }
            seatCandidateUs = null
            if (seatedSinceUs == null) seatedSinceUs = time
            val channels = mutableSetOf("seated")
            if (settings.awayEnabled) channels += "valid_Away"
            val enabled = mapOf(EventKind.HeadDown to settings.headDownEnabled,
                EventKind.HeadTilt to settings.headTiltEnabled, EventKind.BodyLean to settings.bodyLeanEnabled)
            val thresholdScale = when (settings.sensitivity) { Sensitivity.Low -> 1.3f; Sensitivity.Standard -> 1f; Sensitivity.High -> .75f }
            val values = mapOf(
                EventKind.HeadDown to feature?.headHeight?.let { current.headHeight - it },
                EventKind.HeadTilt to feature?.headAngle?.let { abs(it - current.headAngle) },
                EventKind.BodyLean to feature?.bodyAngle?.let { angle -> current.bodyAngle?.let { abs(angle - it) } },
            )
            for ((kind, value) in values) {
                if (enabled[kind] == true && value != null) {
                    channels += "valid_${kind.name}"
                    val threshold = (if (kind == EventKind.HeadDown) .25f else .22f) * thresholdScale
                    val confirm = when (kind) { EventKind.HeadDown -> settings.headDownConfirmMs; EventKind.HeadTilt -> settings.headTiltConfirmMs; else -> settings.bodyLeanConfirmMs }
                    advance(kind, value, threshold, time, confirm * 1_000, revisionId)
                } else closeCandidate(kind, time, if (enabled[kind] == false) "detection_off" else "unknown")
            }
            updateCoverage(channels, time, revisionId)
            return feedback(true, false, false)
        }
        if (settings.awayEnabled && (seatState == "seated" && trackedDeparture || seatState == "leaving" && (outside || clearEmptySeat))) {
            if (seatState == "seated") { seatCandidateUs = time; seatState = "leaving" }
            closePosture(time, "unknown")
            if (clearEmptySeat && time - checkNotNull(seatCandidateUs) >= settings.awayConfirmMs * 1_000) {
                seatState = "away"
                awayId = newId()
                activeAway = EventEntity(newId(), sessionId, EventKind.Away.name, checkNotNull(seatCandidateUs), time, time, checkNotNull(awayId), "active", revisionId)
                events[checkNotNull(activeAway).id] = checkNotNull(activeAway)
                if (restDeadlineUs?.let { checkNotNull(seatCandidateUs) <= it } == true) restUntilUs = checkNotNull(seatCandidateUs) + DefaultMonitorConfig.restWindowMs * 1_000
                restDeadlineUs = null
                seatedSinceUs = null
                seatCandidateUs = null
            } else return unknown(time, revisionId, resetCandidate = false)
        }
        if (settings.awayEnabled && awayId != null && clearEmptySeat) {
            // 已观察到离开后，只有座位区域仍匹配空景基准才能延续离座。
            seatState = "away"
            seatCandidateUs = null
            var event = activeAway
            if (event == null) event = EventEntity(newId(), sessionId, EventKind.Away.name, time, time, time, checkNotNull(awayId), "active", revisionId)
            event = event.copy(endUs = time)
            events[event.id] = event
            activeAway = event
            updateCoverage(setOf("away_normal", "valid_Away"), time, revisionId)
            val end = restUntilUs
            if (end != null) {
                val restEnd = if (time < end) time else end
                val observedStart = checkNotNull(coverage[openCoverage["away_normal"]]).startUs
                val restStart = if (event.startUs > observedStart) event.startUs else observedStart
                if (restEnd > restStart) rests[event.id] = RestWindowEntity(event.id, sessionId, event.id, restStart, restEnd)
            }
            closePosture(time, "unknown")
            return feedback(false, true, false)
        }
        return unknown(time, revisionId)
    }

    fun unknown(time: Long, revisionId: String, resetCandidate: Boolean = true): Feedback {
        closePosture(lastUs ?: time, "unknown")
        activeAway?.let { events[it.id] = it.copy(endReason = "unknown") }
        activeAway = null
        baseline.clear()
        seatedSinceUs = null
        if (resetCandidate) { restDeadlineUs = null; seatCandidateUs = null; seatState = "unknown" }
        if (calibration != null) {
            val active = uncertain ?: newId().let { EventEntity(it, sessionId, EventKind.Uncertain.name, time, time, time, it, "active", revisionId) }
            uncertain = active.copy(endUs = time)
            events[active.id] = checkNotNull(uncertain)
        }
        updateCoverage(setOf(if (calibration == null) "prepare" else "unknown"), time, revisionId)
        return feedback(false, false, true)
    }
    fun factsAtConfigBoundary(): Facts = facts().let { facts ->
        facts.copy(events = facts.events.map { if (it.endReason == "active") it.copy(endReason = "config_changed") else it })
    }
    fun interrupt(time: Long, revisionId: String) {
        val id = newId()
        events[id] = EventEntity(id, sessionId, EventKind.Interruption.name, time, time, time, id, "interrupted", revisionId)
    }
    fun changeConfig(time: Long) {
        uncertain?.let { events[it.id] = it.copy(endReason = "config_changed") }; uncertain = null
        openCoverage.clear()
        for (candidate in candidates.values) {
            candidate.startUs = null
            candidate.recoveryUs = null
            candidate.event?.let { events[it.id] = it.copy(endReason = "config_changed") }
            candidate.event = candidate.event?.copy(id = newId(), startUs = time, endUs = time)
        }
        activeAway?.let { events[it.id] = it.copy(endReason = "config_changed") }
        activeAway = null
    }
    fun close(time: Long, reason: String) {
        closePosture(time, reason)
        activeAway?.let { events[it.id] = it.copy(endReason = reason) }
        activeAway = null
        uncertain?.let { events[it.id] = it.copy(endReason = reason) }
        uncertain = null
        openCoverage.clear()
        baseline.clear()
        seatCandidateUs = null
        seatedSinceUs = null
        restDeadlineUs = null
        if (reason == "unknown") seatState = "unknown"
    }
    fun facts() = Facts(events.values.toList(), coverage.values.toList(), rests.values.toList())
    fun allowedAwayUs(settings: MonitorSettings): Long = settings.maxAwayMs * 1_000
    fun ordinaryAwayUs(): Long {
        val id = awayId ?: return 0
        val spans = events.values.filter { it.logicalEventId == id }.map { EvaluationEngine.Span(it.startUs, it.endUs) }
        val observed = coverage.values.filter { it.channel == "away_normal" }.map { EvaluationEngine.Span(it.startUs, it.endUs) }
        return EvaluationEngine.length(EvaluationEngine.subtract(EvaluationEngine.intersect(spans, observed),
            rests.values.map { EvaluationEngine.Span(it.startUs, it.endUs) }))
    }
    private fun advance(kind: EventKind, value: Float, threshold: Float, time: Long, confirmUs: Long, revision: String) {
        val candidate = candidates.getOrPut(kind) { Candidate() }
        val current = candidate.event
        if (current == null) {
            if (value >= threshold) {
                if (candidate.startUs == null) candidate.startUs = time
                if (time - checkNotNull(candidate.startUs) >= confirmUs) {
                    val id = newId()
                    candidate.event = EventEntity(id, sessionId, kind.name, checkNotNull(candidate.startUs), time, time, id, "active", revision)
                }
            } else candidate.startUs = null
        } else {
            if (value < threshold * .7f) {
                if (candidate.recoveryUs == null) candidate.recoveryUs = time
                if (time - checkNotNull(candidate.recoveryUs) >= DefaultMonitorConfig.recoveryMs * 1_000) {
                    events[current.id] = current.copy(endUs = checkNotNull(candidate.recoveryUs), endReason = "recovered")
                    candidate.event = null; candidate.startUs = null; candidate.recoveryUs = null
                    return
                }
            } else candidate.recoveryUs = null
            candidate.event = current.copy(endUs = time, configRevisionId = revision)
        }
        candidate.event?.let { events[it.id] = it }
    }
    private fun closeCandidate(kind: EventKind, time: Long, reason: String) {
        val candidate = candidates[kind] ?: return
        candidate.event?.let { events[it.id] = it.copy(endUs = if (it.endUs < time) it.endUs else time, endReason = reason) }
        candidates.remove(kind)
    }
    private fun closePosture(time: Long, reason: String) { candidates.keys.toList().forEach { closeCandidate(it, time, reason) } }
    private fun updateCoverage(channels: Set<String>, time: Long, revision: String) {
        openCoverage.keys.filter { it !in channels }.forEach(openCoverage::remove)
        channels.forEach { channel ->
            val id = openCoverage.getOrPut(channel, newId)
            val previous = coverage[id]
            coverage[id] = if (previous == null) CoverageEntity(id, sessionId, channel, time, time, if (channel == "unknown") "low_conf" else "none", revision)
                else previous.copy(endUs = time)
        }
    }
    private fun feedback(seated: Boolean, away: Boolean, unclear: Boolean): Feedback {
        if (!unclear) { uncertain?.let { events[it.id] = it.copy(endReason = "recovered") }; uncertain = null }
        lastFeedback = Feedback(seated, away, unclear, guardian, candidates.filterValues { it.event != null }.keys.toSet(), calibration)
        return lastFeedback
    }
    private fun features(observation: Observation): Feature? {
        fun point(name: String) = observation.landmarks[name]?.takeIf { it.confidence >= .65f }
        val left = point("leftShoulder") ?: return null
        val right = point("rightShoulder") ?: return null
        val nose = point("nose")
        val width = abs(left.x - right.x)
        if (width < .08f) return null
        val center = Point((left.x + right.x) / 2, (left.y + right.y) / 2)
        val leftEar = point("leftEar") ?: point("leftEye")
        val rightEar = point("rightEar") ?: point("rightEye")
        val headAngle = if (leftEar != null && rightEar != null) (leftEar.y - rightEar.y - (left.y - right.y)) / width else null
        val leftHip = point("leftHip")
        val rightHip = point("rightHip")
        val bodyAngle = if (leftHip != null && rightHip != null) (center.x - (leftHip.x + rightHip.x) / 2) / width else null
        return Feature(nose?.let { (center.y - it.y) / width }, headAngle, bodyAngle, width, center)
    }
}
