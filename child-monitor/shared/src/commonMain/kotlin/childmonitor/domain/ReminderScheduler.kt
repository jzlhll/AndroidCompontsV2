package childmonitor.domain

import childmonitor.model.DefaultMonitorConfig
import childmonitor.model.EventKind
import childmonitor.model.MonitorSettings

/** 调度只根据当前仍有效的条件，实际播放时间由平台回调确认。 */
class ReminderScheduler {
    private val lastStarted = mutableMapOf<String, Long>()
    private var lastFinishedUs: Long? = null
    private var restSentFor: Long? = null
    var playing: String? = null
        private set

    fun next(now: Long, feedback: DetectionEngine.Feedback, settings: MonitorSettings,
        invalidSinceUs: Long?, ordinaryAwayUs: Long, allowedAwayUs: Long, seatedSinceUs: Long?): String? {
        if (playing != null || lastFinishedUs?.let { now - it < settings.reminderGapMs * 1_000 } == true) return null
        val desired = when {
            feedback.unclear && invalidSinceUs?.let { now - it >= DefaultMonitorConfig.unclearRemindMs * 1_000 } == true -> "reminder_unclear"
            feedback.away && ordinaryAwayUs > allowedAwayUs -> "reminder_away"
            feedback.activeKinds.size > 1 -> "reminder_posture"
            EventKind.HeadDown in feedback.activeKinds -> "reminder_head_down"
            EventKind.HeadTilt in feedback.activeKinds -> "reminder_head_tilt"
            EventKind.BodyLean in feedback.activeKinds -> "reminder_body_lean"
            settings.restRemindEnabled && seatedSinceUs != null && restSentFor != seatedSinceUs &&
                now - seatedSinceUs >= settings.restRemindMs * 1_000 -> "reminder_rest"
            else -> null
        } ?: return null
        if (lastStarted[desired]?.let { now - it < settings.repeatReminderMs * 1_000 } == true) return null
        playing = desired
        return desired
    }
    fun stillValid(kind: String, feedback: DetectionEngine.Feedback): Boolean = when (kind) {
        "reminder_unclear" -> feedback.unclear
        "reminder_away" -> feedback.away
        "reminder_rest" -> feedback.seated
        "reminder_head_down" -> EventKind.HeadDown in feedback.activeKinds
        "reminder_head_tilt" -> EventKind.HeadTilt in feedback.activeKinds
        "reminder_body_lean" -> EventKind.BodyLean in feedback.activeKinds
        "reminder_posture" -> feedback.activeKinds.isNotEmpty()
        else -> false
    }
    fun started(kind: String, atUs: Long, seatedSinceUs: Long?) {
        lastStarted[kind] = atUs
        if (kind == "reminder_rest") restSentFor = seatedSinceUs
    }
    fun finished(atUs: Long) { playing = null; lastFinishedUs = atUs }
    fun clear() { playing = null; lastStarted.clear(); lastFinishedUs = null; restSentFor = null }
}
