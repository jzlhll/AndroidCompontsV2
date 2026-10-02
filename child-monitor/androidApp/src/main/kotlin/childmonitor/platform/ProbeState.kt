package childmonitor.platform

enum class ProbePhase { Idle, Starting, Recording, Finishing, Complete, Failed, Canceled }
enum class ProbeError {
    PermissionDenied, CameraBusy, NoFrontCamera, UnsupportedSize, CaptureFailed, ModelFailed,
    NoAnalysis, FinalizeFailed, InvalidMedia, Timeout, CleanupFailed,
}

data class ProbeState(
    val generation: Long = 0,
    val phase: ProbePhase = ProbePhase.Idle,
    val analysisFrames: Int = 0,
    val poseFrames: Int = 0,
    val error: ProbeError? = null,
    val captureReleased: Boolean = true,
    val report: ProbeReport? = null,
) {
    val busy: Boolean get() = phase in setOf(ProbePhase.Starting, ProbePhase.Recording, ProbePhase.Finishing)
}

data class ProbeReport(
    val width: Int,
    val height: Int,
    val firstVideoPtsUs: Long,
    val lastVideoPtsUs: Long,
    val firstAnalysisTimeNs: Long,
    val lastAnalysisTimeNs: Long,
    val startCallbackElapsedNs: Long,
    val analysisFrames: Int,
    val poseFrames: Int,
)
