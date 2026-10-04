package childmonitor.model

import kotlinx.serialization.Serializable

const val CURRENT_DETECTION_MODEL_ID = "mlkit-pose-beta5/face-16.1.7/efficientdet-lite0-int8-v2"

@Serializable
enum class RunState { Idle, Starting, Preparing, Monitoring, Stopping, Stopped }
@Serializable
enum class SaveState { Finalizing, Saved, RetryableFailure, Unrecoverable, MetadataOnly }
@Serializable
enum class EndReason { UserStop, TargetReached, SystemLocked, Background, CameraInterrupted, CaptureFailed, IneffectiveTimeout, StorageLow, PrepareTimeout }
@Serializable
enum class EventKind { HeadDown, LeanForward, HeadTilt, BodyLean, Away, Return, Uncertain, Interruption, FaceIncomplete, SceneChange }

@Serializable
data class Point(val x: Float, val y: Float, val confidence: Float = 1f) {
    val inFrame: Boolean get() = x in .02f..0.98f && y in .02f..0.98f
}
@Serializable
data class SeatRegion(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun validate() {
        require(listOf(left, top, right, bottom).all { it.isFinite() && it in 0f..1f })
        require(right - left >= .15f && bottom - top >= .15f)
    }
    fun contains(point: Point) = point.x in left..right && point.y in top..bottom
}

@Serializable
data class Calibration(val headHeight: Float, val headAngle: Float,
    val bodyAngle: Float?, val shoulderWidth: Float, val transformVersion: Int, val version: Long,
    val facePitch: Float = 0f, val faceYaw: Float = 0f,
    // 兼容历史配置快照，新会话不再保存或使用座位区域。
    val roi: SeatRegion? = null)

data class FaceObservation(val bounds: SeatRegion, val complete: Boolean,
    val pitch: Float, val yaw: Float, val largeEnough: Boolean)

data class DetectedObject(val category: String, val bounds: SeatRegion)

data class SceneCell(val luminance: Float, val redChroma: Float, val blueChroma: Float, val texture: Float)
data class SceneGrid(val columns: Int, val rows: Int, val cells: List<SceneCell>)

data class Observation(
    val sessionId: String,
    val generation: Long,
    val frameTimeUs: Long,
    val transformVersion: Int,
    val landmarks: Map<String, Point>,
    val people: List<SeatRegion>,
    val sceneClear: Boolean,
    val deviceMoved: Boolean,
    val sceneGrid: SceneGrid? = null,
    val faces: List<FaceObservation> = emptyList(),
    val objects: List<DetectedObject> = emptyList(),
    val modelId: String = CURRENT_DETECTION_MODEL_ID,
    val mappingVersion: Int = 1,
)

@Serializable
data class SessionManifest(
    val formatVersion: Int,
    val sessionId: String,
    val relativeDir: String,
    val timezoneId: String,
    val startedWallUs: Long,
    val videoOriginOffsetUs: Long,
    val stage: String,
    val stagingFile: String,
    val finalFile: String,
    val lastCheckpointUs: Long,
    val writtenAtUs: Long,
)

data class StorageOverview(val usedBytes: Long, val availableBytes: Long, val pendingCount: Int)

data class MediaInfo(val durationUs: Long, val width: Int, val height: Int)
data class RecordFilter(val fromDate: String? = null, val toDate: String? = null, val status: String = "all", val query: String = "") {
    fun validate() {
        fromDate?.let { java.time.LocalDate.parse(it) }
        toDate?.let { java.time.LocalDate.parse(it) }
        require(fromDate == null || toDate == null || fromDate <= toDate)
        require(status in listOf("all", "normal", "interrupted", "pending", "metadata"))
        require(query.length <= 100)
    }
}
data class DailyStatistics(val date: String, val sessions: Int, val durationUs: Long, val observedUs: Long,
    val seatedUs: Long, val awayCount: Int, val items: List<ItemStatistics>)

data class SessionCursor(val startedWallUs: Long, val id: String)
data class SessionPage<T>(val items: List<T>, val nextCursor: SessionCursor?)

@Serializable
data class EvaluationSnapshot(
    val version: Int = 1,
    val durationUs: Long,
    val grade: String?,
    val completionOnly: Boolean,
    val partialData: Boolean,
    val seatedUs: Long,
    val awayUs: Long,
    val restUs: Long,
    val prepareUs: Long,
    val unknownUs: Long,
    val awayCount: Int,
    val timeoutAwayCount: Int,
    val items: List<ItemStatistics>,
)
@Serializable
data class ItemStatistics(val kind: EventKind, val count: Int, val abnormalUs: Long, val validUs: Long, val needsSuggestion: Boolean = false)
