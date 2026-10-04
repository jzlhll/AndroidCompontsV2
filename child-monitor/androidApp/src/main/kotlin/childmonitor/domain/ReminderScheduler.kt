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
        val posture = feedback.activeKinds - setOf(EventKind.SceneChange, EventKind.FaceIncomplete)
        val desired = when {
            feedback.unclear && invalidSinceUs?.let { now - it >= DefaultMonitorConfig.unclearRemindMs * 1_000 } == true -> "reminder_unclear"
            feedback.away && ordinaryAwayUs > allowedAwayUs -> "reminder_away"
            posture.size > 1 -> "reminder_posture"
            EventKind.HeadDown in posture -> "reminder_head_down"
            EventKind.HeadTilt in posture -> "reminder_head_tilt"
            EventKind.BodyLean in posture -> "reminder_body_lean"
            EventKind.FaceIncomplete in feedback.activeKinds -> "reminder_posture"
            settings.restRemindEnabled && seatedSinceUs != null && restSentFor != seatedSinceUs &&
                now - seatedSinceUs >= settings.restRemindMs * 1_000 -> "reminder_rest"
            else -> null
        } ?: return null
        if (lastStarted[desired]?.let { now - it < settings.repeatReminderMs * 1_000 } == true) return null
        playing = desired
        return desired
    }
    fun stillValid(kind: String, feedback: DetectionEngine.Feedback, settings: MonitorSettings, now: Long,
        ordinaryAwayUs: Long, allowedAwayUs: Long, seatedSinceUs: Long?): Boolean = when (kind) {
        "reminder_unclear" -> feedback.unclear
        "reminder_away" -> settings.awayEnabled && feedback.away && ordinaryAwayUs > allowedAwayUs
        "reminder_rest" -> settings.restRemindEnabled && feedback.seated && seatedSinceUs != null && now - seatedSinceUs >= settings.restRemindMs * 1_000
        "reminder_head_down" -> settings.headDownEnabled && EventKind.HeadDown in feedback.activeKinds
        "reminder_head_tilt" -> settings.headTiltEnabled && EventKind.HeadTilt in feedback.activeKinds
        "reminder_body_lean" -> settings.bodyLeanEnabled && EventKind.BodyLean in feedback.activeKinds
        "reminder_posture" -> (feedback.activeKinds - EventKind.SceneChange).isNotEmpty()
        else -> false
    }
    fun changeConfig(previous: MonitorSettings, settings: MonitorSettings) {
        if (previous.headDownEnabled != settings.headDownEnabled || previous.headDownConfirmMs != settings.headDownConfirmMs || previous.sensitivity != settings.sensitivity) {
            lastStarted.remove("reminder_head_down"); lastStarted.remove("reminder_posture")
        }
        if (previous.headTiltEnabled != settings.headTiltEnabled || previous.headTiltConfirmMs != settings.headTiltConfirmMs || previous.sensitivity != settings.sensitivity) {
            lastStarted.remove("reminder_head_tilt"); lastStarted.remove("reminder_posture")
        }
        if (previous.bodyLeanEnabled != settings.bodyLeanEnabled || previous.bodyLeanConfirmMs != settings.bodyLeanConfirmMs || previous.sensitivity != settings.sensitivity) {
            lastStarted.remove("reminder_body_lean"); lastStarted.remove("reminder_posture")
        }
        if (previous.awayEnabled != settings.awayEnabled || previous.awayConfirmMs != settings.awayConfirmMs) lastStarted.remove("reminder_away")
        if (previous.restRemindEnabled != settings.restRemindEnabled || previous.restRemindMs != settings.restRemindMs) {
            restSentFor = null
            lastStarted.remove("reminder_rest")
        }
    }
    fun started(kind: String, atUs: Long, seatedSinceUs: Long?) {
        lastStarted[kind] = atUs
        if (kind == "reminder_rest") restSentFor = seatedSinceUs
    }
    fun finished(atUs: Long) { playing = null; lastFinishedUs = atUs }
    fun clear() { playing = null; lastStarted.clear(); lastFinishedUs = null; restSentFor = null }
}
