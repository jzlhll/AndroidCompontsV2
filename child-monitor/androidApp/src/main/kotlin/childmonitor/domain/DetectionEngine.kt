package childmonitor.domain

import childmonitor.data.database.entity.*
import childmonitor.model.*
import kotlin.math.abs

/** 在全画面自动校准，记录姿态、人脸完整性和场景变化，未知区间截断连续性。 */
class DetectionEngine(private val sessionId: String, private val newId: () -> String) {
    data class Facts(val events: List<EventEntity>, val coverage: List<CoverageEntity>, val rest: List<RestWindowEntity>)
    data class Feedback(val seated: Boolean, val away: Boolean, val unclear: Boolean,
        val needsGuardian: Boolean, val activeKinds: Set<EventKind>, val calibration: Calibration?,
        val faceComplete: Boolean = false, val sceneChanged: Boolean = false)
    private data class Feature(val headHeight: Float?, val headAngle: Float?, val bodyAngle: Float?, val shoulderWidth: Float, val center: Point)
    private data class Sample(val time: Long, val feature: Feature, val face: FaceObservation)
    private data class Candidate(var startUs: Long? = null, var recoveryUs: Long? = null,
        var event: EventEntity? = null, var logicalEventId: String? = null)
    var calibration: Calibration? = null
        private set
    private val sceneChanges = SceneChangeTracker()
    private var faceComplete = false
    private var sceneChanged = false
    var placementIssue = "head"
        private set
    val placementProgress: Float get() = baseline.firstOrNull()?.let {
        (baseline.last().time - it.time).toFloat() / (DefaultMonitorConfig.calibrationStableMs * 1_000)
    } ?: 0f
    private val baseline = mutableListOf<Sample>()
    private val candidates = mutableMapOf<EventKind, Candidate>()
    private val events = linkedMapOf<String, EventEntity>()
    private val coverage = linkedMapOf<String, CoverageEntity>()
    private val openCoverage = mutableMapOf<String, String>()
    private val rests = linkedMapOf<String, RestWindowEntity>()
    private var lastUs: Long? = null
    private var seatState = "uninitialized"
    private var seatCandidateUs: Long? = null
    private var awayId: String? = null
    private var pendingAwayId: String? = null
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

    fun reposition(atUs: Long) {
        close(atUs, "unknown")
        calibration = null
        placementIssue = "head"
        sceneChanges.reset()
        faceComplete = false
        sceneChanged = false
        awayId = null
        pendingAwayId = null
        lastUs = null
        guardian = false
        seatState = "uninitialized"
        restUntilUs = null
        feedback(false, false, true)
    }
    fun allowRest(startedUs: Long) { restDeadlineUs = startedUs + DefaultMonitorConfig.responseWindowMs * 1_000 }

    /** 先生成可事务提交的旧配置事实，数据库成功后才切换内存状态。 */
    fun factsAtConfigBoundary(settings: MonitorSettings): Facts = Facts(events.values.map { event ->
        if (event.endReason != "active") event else event.copy(endReason = when {
            event.kind == EventKind.HeadDown.name && !settings.headDownEnabled ||
                event.kind == EventKind.HeadTilt.name && !settings.headTiltEnabled ||
                event.kind == EventKind.BodyLean.name && !settings.bodyLeanEnabled ||
                event.kind == EventKind.Away.name && !settings.awayEnabled -> "detection_off"
            else -> "config_changed"
        })
    }, coverage.values.toList(), rests.values.toList())

    /** 保留校准和未变更项目的计时；跨版本事件分段但沿用逻辑事件标识。 */
    fun changeConfig(time: Long, previous: MonitorSettings, settings: MonitorSettings, revision: String, facts: Facts) {
        facts.events.forEach { events[it.id] = it }
        val changed = buildSet {
            if (previous.headDownEnabled != settings.headDownEnabled || previous.headDownConfirmMs != settings.headDownConfirmMs || previous.sensitivity != settings.sensitivity) add(EventKind.HeadDown)
            if (previous.headTiltEnabled != settings.headTiltEnabled || previous.headTiltConfirmMs != settings.headTiltConfirmMs || previous.sensitivity != settings.sensitivity) add(EventKind.HeadTilt)
            if (previous.bodyLeanEnabled != settings.bodyLeanEnabled || previous.bodyLeanConfirmMs != settings.bodyLeanConfirmMs || previous.sensitivity != settings.sensitivity) add(EventKind.BodyLean)
        }
        changed.forEach { kind ->
            val candidate = candidates.remove(kind)
            val enabled = when (kind) {
                EventKind.HeadDown -> settings.headDownEnabled
                EventKind.HeadTilt -> settings.headTiltEnabled
                else -> settings.bodyLeanEnabled
            }
            if (enabled && candidate?.event != null) candidates[kind] = Candidate(startUs = time,
                logicalEventId = checkNotNull(candidate.event).logicalEventId)
        }
        fun continuation(event: EventEntity): EventEntity = event.copy(id = newId(), startUs = time,
            endUs = time, configRevisionId = revision).also { events[it.id] = it }
        candidates.values.forEach { candidate -> candidate.event = candidate.event?.let(::continuation) }
        if (previous.awayEnabled != settings.awayEnabled || previous.awayConfirmMs != settings.awayConfirmMs) {
            pendingAwayId = if (settings.awayEnabled) awayId ?: pendingAwayId else null
            activeAway = null
            awayId = null
            seatCandidateUs = null
            if (!settings.awayEnabled) { restUntilUs = null; restDeadlineUs = null }
            if (seatState != "seated") seatState = "unknown"
        } else activeAway = activeAway?.let(::continuation)
        uncertain = uncertain?.let(::continuation)
        // 新段从配置生效时间开始，未受影响的覆盖通道保持连续。
        val changedChannels = changed.map { "valid_${it.name}" }
        val channels = openCoverage.keys.filter { channel ->
            channel !in changedChannels &&
                (settings.awayEnabled || channel !in listOf("away_normal", "valid_Away"))
        }
        openCoverage.clear()
        channels.forEach { channel ->
            val id = newId()
            coverage[id] = CoverageEntity(id, sessionId, channel, time, time,
                if (channel == "unknown") "low_conf" else "none", revision)
            openCoverage[channel] = id
        }
        if (previous.restRemindEnabled && !settings.restRemindEnabled) restDeadlineUs = null
        feedback(lastFeedback.seated, lastFeedback.away && activeAway != null,
            lastFeedback.unclear || lastFeedback.away && activeAway == null)
    }

    fun accept(observation: Observation, settings: MonitorSettings, revisionId: String): Feedback {
        val time = observation.frameTimeUs
        val prior = lastUs
        if (prior != null && time <= prior) return lastFeedback
        if (prior != null && time - prior > DefaultMonitorConfig.evidenceMaxGapMs * 1_000) {
            close(prior, "unknown")
            sceneChanges.reset()
        }
        lastUs = time
        val feature = features(observation)
        val face = observation.faces.singleOrNull()
        val modelAmbiguous = observation.people.size > 1 || observation.faces.size > 1
        val person = observation.people.singleOrNull()
        val nose = observation.landmarks["nose"]?.takeIf { it.confidence >= .65f && it.inFrame }
        val associatedFace = face?.takeIf { person?.contains(Point((it.bounds.left + it.bounds.right) / 2,
            (it.bounds.top + it.bounds.bottom) / 2)) == true && (nose == null || it.bounds.contains(nose)) }
        faceComplete = associatedFace?.complete == true
        sceneChanged = false
        if (observation.deviceMoved || calibration?.transformVersion?.let { it != observation.transformVersion } == true) guardian = true
        placementIssue = when {
            guardian -> "moved"
            modelAmbiguous -> "multiple"
            !observation.sceneClear -> "lighting"
            person == null -> "person"
            !faceComplete -> if (face?.largeEnough == false) "face_small" else "head"
            feature == null -> "shoulders"
            feature.headAngle == null || feature.headHeight == null -> "head"
            abs(feature.headAngle) > .3f || feature.headHeight < .15f || feature.bodyAngle?.let { abs(it) > .4f } == true ||
                associatedFace?.let { abs(it.pitch) > 15f || abs(it.yaw) > 15f } == true -> "upright"
            else -> "stable"
        }
        if (guardian || !observation.sceneClear || modelAmbiguous) {
            faceComplete = false
            return unknown(time, revisionId)
        }
        if (calibration == null) {
            updateCoverage(setOf("prepare"), time, revisionId)
            if (placementIssue != "stable" || feature == null || associatedFace == null) {
                baseline.clear()
            } else {
                baseline += Sample(time, feature, associatedFace)
                val first = baseline.first()
                if (time - first.time >= DefaultMonitorConfig.calibrationStableMs * 1_000) {
                    fun median(values: List<Float>) = values.sorted()[values.size / 2]
                    val heights = baseline.mapNotNull { it.feature.headHeight }
                    val widths = baseline.map { it.feature.shoulderWidth }
                    val headHeight = median(heights)
                    val shoulderWidth = median(widths)
                    val stable = heights.all { abs(it - headHeight) < .15f } && widths.all { abs(it - shoulderWidth) < .05f } &&
                        baseline.all { abs(it.feature.center.x - first.feature.center.x) < .05f && abs(it.feature.center.y - first.feature.center.y) < .05f }
                    if (stable) {
                        calibrationVersion++
                        calibration = Calibration(headHeight, median(baseline.mapNotNull { it.feature.headAngle }),
                            baseline.mapNotNull { it.feature.bodyAngle }.let { if (it.size == baseline.size) median(it) else null },
                            shoulderWidth, observation.transformVersion, calibrationVersion,
                            median(baseline.map { it.face.pitch }), median(baseline.map { it.face.yaw }))
                        seatState = "seated"
                        seatedSinceUs = time
                        sceneChanges.observe(observation, subjectPresent = true, rememberSubject = true)
                        baseline.clear()
                        return feedback(true, false, false)
                    } else baseline.clear()
                }
            }
            return feedback(false, false, true)
        }
        val current = checkNotNull(calibration)
        val subjectPresent = person != null || observation.faces.isNotEmpty() || feature != null
        val change = sceneChanges.observe(observation, subjectPresent, rememberSubject = person != null && faceComplete)
        sceneChanged = change.backgroundChanged
        if (person != null) {
            if (seatState != "seated") {
                // 回到画面时仍需完整人脸，半脸或边缘人体不能确认恢复。
                if (!faceComplete) return unknown(time, revisionId)
                if (seatState != "returning") { seatState = "returning"; seatCandidateUs = time }
                if (time - checkNotNull(seatCandidateUs) < DefaultMonitorConfig.recoveryMs * 1_000) return unknown(time, revisionId, resetCandidate = false)
                activeAway?.let { events[it.id] = it.copy(endUs = checkNotNull(seatCandidateUs), endReason = "recovered") }
                if (awayId != null) {
                    val id = newId()
                    events[id] = EventEntity(id, sessionId, EventKind.Return.name, checkNotNull(seatCandidateUs), checkNotNull(seatCandidateUs), time, id, "recovered", revisionId)
                }
                activeAway = null
                awayId = null
                pendingAwayId = null
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
            val heightChange = feature?.headHeight?.let { current.headHeight - it }
            val pitchChange = associatedFace?.pitch?.takeIf { it.isFinite() }?.let { (current.facePitch - it) / 90f }
            val angleChange = feature?.headAngle?.let { abs(it - current.headAngle) }
            val yawChange = associatedFace?.yaw?.takeIf { it.isFinite() }?.let { abs(it - current.faceYaw) / 90f }
            val values = mapOf(
                EventKind.HeadDown to listOfNotNull(heightChange, pitchChange).maxOrNull(),
                EventKind.HeadTilt to listOfNotNull(angleChange, yawChange).maxOrNull(),
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
            advance(EventKind.FaceIncomplete, if (faceComplete) 0f else 1f, .5f, time,
                DefaultMonitorConfig.seatedConfirmMs * 1_000, revisionId)
            advance(EventKind.SceneChange, if (sceneChanged) 1f else 0f, .5f, time,
                DefaultMonitorConfig.seatedConfirmMs * 1_000, revisionId)
            if (faceComplete && sceneChanged && candidates[EventKind.SceneChange]?.event != null) {
                sceneChanges.confirmChange(observation)
            }
            updateCoverage(channels, time, revisionId)
            return feedback(true, false, false)
        }
        if (settings.awayEnabled && change.absent) {
            closePosture(time, "unknown")
            if (awayId == null) {
                if (seatState != "leaving") { seatCandidateUs = time; seatState = "leaving" }
                if (time - checkNotNull(seatCandidateUs) < settings.awayConfirmMs * 1_000) return unknown(time, revisionId, resetCandidate = false)
                awayId = pendingAwayId ?: newId()
                pendingAwayId = null
                activeAway = EventEntity(newId(), sessionId, EventKind.Away.name, checkNotNull(seatCandidateUs), time, time,
                    checkNotNull(awayId), "active", revisionId)
                if (restDeadlineUs?.let { checkNotNull(seatCandidateUs) <= it } == true) restUntilUs = checkNotNull(seatCandidateUs) + DefaultMonitorConfig.restWindowMs * 1_000
                restDeadlineUs = null
            }
            seatState = "away"
            seatedSinceUs = null
            seatCandidateUs = null
            var event = activeAway ?: EventEntity(newId(), sessionId, EventKind.Away.name, time, time, time, checkNotNull(awayId), "active", revisionId)
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
            return feedback(false, true, false)
        }
        return unknown(time, revisionId)
    }

    fun unknown(time: Long, revisionId: String, resetCandidate: Boolean = true): Feedback {
        if (lastUs == null || time > checkNotNull(lastUs)) {
            faceComplete = false
            sceneChanged = false
            sceneChanges.reset()
        }
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
    fun interrupt(time: Long, revisionId: String) {
        val id = newId()
        events[id] = EventEntity(id, sessionId, EventKind.Interruption.name, time, time, time, id, "interrupted", revisionId)
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
                    candidate.event = EventEntity(id, sessionId, kind.name, checkNotNull(candidate.startUs), time, time,
                        candidate.logicalEventId ?: id, "active", revision)
                }
            } else { candidate.startUs = null; candidate.logicalEventId = null }
        } else {
            if (value < threshold * .7f) {
                if (candidate.recoveryUs == null) candidate.recoveryUs = time
                if (time - checkNotNull(candidate.recoveryUs) >= DefaultMonitorConfig.recoveryMs * 1_000) {
                    val recoveredUs = checkNotNull(candidate.recoveryUs)
                    events[current.id] = current.copy(endUs = if (recoveredUs < current.startUs) current.startUs else recoveredUs, endReason = "recovered")
                    candidate.event = null; candidate.startUs = null; candidate.recoveryUs = null; candidate.logicalEventId = null
                    return
                }
            } else candidate.recoveryUs = null
            candidate.event = current.copy(endUs = time, configRevisionId = revision)
        }
        candidate.event?.let { events[it.id] = it }
    }
    private fun closeCandidate(kind: EventKind, time: Long, reason: String) {
        val candidate = candidates[kind] ?: return
        candidate.event?.let { events[it.id] = it.copy(endUs = when {
            time < it.startUs -> it.startUs
            it.endUs < time -> it.endUs
            else -> time
        }, endReason = reason) }
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
        lastFeedback = Feedback(seated, away, unclear, guardian, candidates.filterValues { it.event != null }.keys.toSet(), calibration, faceComplete, sceneChanged)
        return lastFeedback
    }
    private fun features(observation: Observation): Feature? {
        fun point(name: String) = observation.landmarks[name]?.takeIf { it.confidence >= .65f && it.inFrame }
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
