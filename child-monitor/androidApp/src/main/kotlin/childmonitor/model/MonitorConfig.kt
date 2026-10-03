package childmonitor.model

import kotlinx.serialization.Serializable

object AvatarCatalog {
    const val defaultId = "bear"
    val ids = listOf(defaultId, "rabbit", "cat")
    fun resolve(id: String) = if (id in ids) id else defaultId
}

@Serializable
enum class Sensitivity { Low, Standard, High }

@Serializable
data class MonitorSettings(
    val formatVersion: Int = 1,
    val headDownEnabled: Boolean = true,
    val headTiltEnabled: Boolean = true,
    val bodyLeanEnabled: Boolean = true,
    val awayEnabled: Boolean = true,
    val sensitivity: Sensitivity = Sensitivity.Standard,
    val headDownConfirmMs: Long = 5_000,
    val headTiltConfirmMs: Long = 5_000,
    val bodyLeanConfirmMs: Long = 5_000,
    val awayConfirmMs: Long = 3_000,
    val maxAwayMs: Long = 120_000,
    val soundEnabled: Boolean = true,
    val soundVolume: Float = .8f,
    val targetDurationMs: Long = 0,
    val targetAutoStop: Boolean = false,
    val repeatReminderMs: Long = 60_000,
    val reminderGapMs: Long = 10_000,
    val restRemindEnabled: Boolean = true,
    val restRemindMs: Long = 1_800_000,
) {
    fun validate() {
        require(formatVersion == 1)
        require(soundVolume.isFinite() && soundVolume in 0f..1f)
        require(targetDurationMs == 0L || targetDurationMs in 60_000..28_800_000)
        require(listOf(headDownConfirmMs, headTiltConfirmMs, bodyLeanConfirmMs, awayConfirmMs,
            maxAwayMs, repeatReminderMs, reminderGapMs, restRemindMs).all { it in 1..86_400_000 })
        require(maxAwayMs > awayConfirmMs && maxAwayMs <= DefaultMonitorConfig.reasonableAwayMs)
    }
}

@Serializable
data class UserPreferences(
    val formatVersion: Int = 1,
    val preferencesRevision: Long = 0,
    val avatarId: String = AvatarCatalog.defaultId,
    val darkenAfterMs: Long = 60_000,
    val retentionDays: Int = 0,
) {
    fun validate() {
        require(formatVersion == 1 && preferencesRevision >= 0)
        require(darkenAfterMs in 5_000..600_000)
        require(retentionDays in listOf(0, 7, 30, 90))
    }
}

data class SettingsSnapshot(
    val monitorRevision: Long,
    val monitorSettings: MonitorSettings,
    val preferences: UserPreferences,
) { val preferencesRevision: Long get() = preferences.preferencesRevision }

data class UserPreferencesPatch(val avatarId: String? = null, val darkenAfterMs: Long? = null, val retentionDays: Int? = null)

/** 所有默认时限使用毫秒配置；业务记录与运算的边界统一转换为微秒。 */
object DefaultMonitorConfig {
    val settings = MonitorSettings()
    val preferences = UserPreferences()
    const val calibrationStableMs = 3_000L
    const val seatedConfirmMs = 3_000L
    const val recoveryMs = 2_000L
    const val evidenceMaxGapMs = 1_000L
    const val reasonableAwayMs = 180_000L
    const val unclearRemindMs = 10_000L
    const val qualityActionMs = 30_000L
    const val ineffectiveTimeoutMs = 120_000L
    const val recoverConfirmMs = 3_000L
    const val responseWindowMs = 120_000L
    const val restWindowMs = 180_000L
    const val startTimeoutMs = 15_000L
    const val stopSaveTimeoutMs = 30_000L
    const val checkpointMs = 5_000L
    const val evaluationMinUs = 180_000_000L
    const val videoBitRate = 2_000_000
    const val estimatedSessionBytes = videoBitRate.toLong() / 8 * 1_800 * 12 / 10
    val lowWaterBytes = if (estimatedSessionBytes > 104_857_600L) estimatedSessionBytes else 104_857_600L
}
