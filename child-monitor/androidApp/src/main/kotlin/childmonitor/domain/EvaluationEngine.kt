package childmonitor.domain

import childmonitor.TAG
import com.au.module_android.log.logdNoFile
import childmonitor.data.database.entity.*
import childmonitor.model.*

/** 只消费持久化事实计算评价，缺少有效分母时不补零、不生成虚假等级。 */
object EvaluationEngine {
    data class Span(val start: Long, val end: Long)

    fun merge(spans: List<Span>): List<Span> {
        val merged = mutableListOf<Span>()
        for (span in spans.filter { it.end > it.start }.sortedBy { it.start }) {
            val previous = merged.lastOrNull()
            if (previous != null && span.start <= previous.end) {
                merged[merged.lastIndex] = Span(previous.start, if (span.end > previous.end) span.end else previous.end)
            } else merged += span
        }
        return merged
    }
    fun length(spans: List<Span>) = merge(spans).sumOf { it.end - it.start }
    fun intersect(left: List<Span>, right: List<Span>): List<Span> = left.flatMap { a ->
        right.mapNotNull { b ->
            val start = if (a.start > b.start) a.start else b.start
            val end = if (a.end < b.end) a.end else b.end
            if (end > start) Span(start, end) else null
        }
    }
    fun subtract(spans: List<Span>, cuts: List<Span>): List<Span> {
        var result = merge(spans)
        for (cut in merge(cuts)) result = result.flatMap { span ->
            if (cut.end <= span.start || cut.start >= span.end) listOf(span)
            else buildList {
                if (cut.start > span.start) add(Span(span.start, cut.start))
                if (cut.end < span.end) add(Span(cut.end, span.end))
            }
        }
        return result
    }

    fun evaluate(session: SessionEntity, events: List<EventEntity>, coverage: List<CoverageEntity>,
        rest: List<RestWindowEntity>, revisions: List<ConfigRevisionEntity>): EvaluationSnapshot {
        val bounds = listOf(Span(0, session.durationUs))
        fun channel(name: String) = intersect(coverage.filter { it.channel == name }.map { Span(it.startUs, it.endUs) }, bounds)
        val restSpans = intersect(rest.map { Span(it.startUs, it.endUs) }, bounds)
        val seated = channel("seated")
        val ordinaryAway = subtract(channel("away_normal"), restSpans)
        val awayEvents = events.filter { it.kind == EventKind.Away.name }.groupBy { it.logicalEventId }
            .values.sortedBy { group -> group.minBy { it.startUs }.startUs }
        var timeouts = 0
        var reasonableSpans = emptyList<Span>()
        for ((index, group) in awayEvents.withIndex()) {
            val portions = intersect(group.map { Span(it.startUs, it.endUs) }, ordinaryAway)
            val duration = length(portions)
            val allowanceUs = DefaultMonitorConfig.reasonableAwayMs * 1_000
            val timedOut = group.any { segment ->
                val config = revisions.firstOrNull { it.id == segment.configRevisionId }?.let {
                    kotlinx.serialization.json.Json.decodeFromString<MonitorSettings>(it.settingsJson)
                } ?: error("Missing event configuration")
                val limit = if (index == 0) allowanceUs else config.maxAwayMs * 1_000
                length(intersect(portions, listOf(Span(0, segment.endUs)))) > limit
            }
            if (timedOut) timeouts++
            if (index == 0 && duration <= allowanceUs) reasonableSpans = portions
        }
        val gradedAway = subtract(ordinaryAway, reasonableSpans)
        val seatedUs = length(seated)
        val awayUs = length(gradedAway)
        val validAway = channel("valid_Away")
        val awaySeatedUs = length(intersect(seated, validAway))
        val denominator = awaySeatedUs + awayUs
        val totalObservedUs = length(seated + ordinaryAway)
        val ranks = mutableListOf<Int>()
        val kinds = listOf(EventKind.HeadDown, EventKind.HeadTilt, EventKind.BodyLean)
        val items = kinds.map { kind ->
            val valid = intersect(channel("valid_${kind.name}"), seated)
            val relevant = events.filter { it.kind == kind.name || kind == EventKind.HeadDown && it.kind == EventKind.LeanForward.name }
            val validUs = length(valid)
            val abnormalUs = length(intersect(relevant.map { Span(it.startUs, it.endUs) }, valid))
            val count = relevant.filter { intersect(listOf(Span(it.startUs, it.endUs)), valid).isNotEmpty() }
                .map { it.logicalEventId }.distinct().size
            var needsSuggestion = false
            if (validUs > 0) {
                val frequency = if (validUs >= 600_000_000L) count * 1_800_000_000.0 / validUs else count.toDouble()
                val great = if (validUs >= 600_000_000L) 3.0 else 1.0
                val good = if (validUs >= 600_000_000L) 6.0 else 2.0
                val fraction = abnormalUs.toDouble() / validUs
                needsSuggestion = frequency > great || fraction > .05
                ranks += when {
                    frequency <= great && fraction <= .05 -> 0
                    frequency <= good && fraction <= .15 -> 1
                    else -> 2
                }
            }
            logdNoFile(tag = TAG) { "evaluation item sessionId=${session.id} kind=$kind count=$count abnormalUs=$abnormalUs validUs=$validUs normalized=${validUs >= 600_000_000L}" }
            ItemStatistics(kind, count, abnormalUs, validUs, needsSuggestion)
        }
        if (denominator >= 300_000_000L && awaySeatedUs > 0) {
            val ratio = awaySeatedUs.toDouble() / denominator
            ranks += when { ratio >= .9 -> 0; ratio >= 2.0 / 3.0 -> 1; else -> 2 }
        }
        if (denominator > 0) {
            val frequency = if (denominator >= 1_200_000_000L) timeouts * 1_800_000_000.0 / denominator else timeouts.toDouble()
            ranks += when { frequency == 0.0 -> 0; frequency <= 1 -> 1; else -> 2 }
        }
        val eligible = session.durationUs > DefaultMonitorConfig.evaluationMinUs
        val fraction = if (session.durationUs > 0) totalObservedUs.toDouble() / session.durationUs else 0.0
        var rank = ranks.sorted().lastOrNull()
        if (rank != null) {
            if (fraction < .2) rank = 2
            else if (fraction < .5 && rank < 1) rank = 1
        }
        val grade = if (!eligible) null else when (rank) { 0 -> "Great"; 1 -> "Good"; 2 -> "NotBad"; else -> null }
        logdNoFile(tag = TAG) { "evaluation calculated sessionId=${session.id} durationUs=${session.durationUs} eligible=$eligible seatedUs=$seatedUs awaySeatedUs=$awaySeatedUs ordinaryAwayUs=${length(ordinaryAway)} gradedAwayUs=$awayUs denominatorUs=$denominator observedUs=$totalObservedUs coverageFraction=$fraction timeoutCount=$timeouts grade=$grade" }
        return EvaluationSnapshot(durationUs = session.durationUs, grade = grade,
            completionOnly = grade == null, partialData = fraction < .5 || items.any { it.validUs == 0L },
            seatedUs = seatedUs, awayUs = length(ordinaryAway), restUs = length(restSpans),
            prepareUs = length(channel("prepare")), unknownUs = length(channel("unknown")),
            awayCount = awayEvents.size, timeoutAwayCount = timeouts, items = items)
    }
}
