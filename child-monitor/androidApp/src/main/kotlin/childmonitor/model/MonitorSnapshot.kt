package childmonitor.model

enum class StorageStatus { Unchecked, Checking, Available, Failed }

data class UiSnapshot(
    val revision: Long = 0,
    val storageStatus: StorageStatus = StorageStatus.Unchecked,
    val availableBytes: Long? = null,
    val checkedAtUs: Long? = null,
    val requestId: String? = null,
    val ready: Boolean = false,
    val settings: SettingsSnapshot? = null,
    val sessionSettings: MonitorSettings? = null,
    val runState: RunState = RunState.Idle,
    val sessionId: String? = null,
    val durationUs: Long = 0,
    val captureReleased: Boolean = true,
    val error: ErrorCode? = null,
    val needsGuardian: Boolean = false,
    val unclear: Boolean = false,
    val seated: Boolean = false,
    val faceComplete: Boolean = false,
    val sceneChanged: Boolean = false,
    val placementIssue: String = "head",
    val placementProgress: Float = 0f,
    val targetReached: Boolean = false,
    val away: Boolean = false,
    val darkened: Boolean = false,
    val reminderId: String? = null,
    val resultId: String? = null,
    val interruptionId: String? = null,
    val operationBusy: Boolean = false,

)

enum class SettingPolicy { Live, NextSession, Stopped }

fun UiSnapshot.canChangeSettings(policy: SettingPolicy = SettingPolicy.Live): Boolean =
    ready && settings != null && !operationBusy && when (runState) {
        RunState.Idle, RunState.Stopped -> captureReleased
        RunState.Preparing, RunState.Monitoring -> policy != SettingPolicy.Stopped
        else -> false
    }

sealed interface CommandResult<out T> {
    val requestId: String

    data class Success<T>(override val requestId: String, val data: T) : CommandResult<T>
    data class Failure(
        override val requestId: String,
        val errorCode: ErrorCode,
        val recoverable: Boolean,
    ) : CommandResult<Nothing>
}

enum class ErrorCode { StorageReadFailed, OperationFailed, InvalidInput, Busy, StorageLow, SettingsConflict, CaptureFailed }
