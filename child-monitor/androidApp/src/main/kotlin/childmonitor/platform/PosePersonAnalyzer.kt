package childmonitor.platform

import childmonitor.TAG
import com.au.module_android.log.logdNoFile
import com.au.module_android.log.loge
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.camera.view.transform.OutputTransform
import childmonitor.model.*
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/** 两个模型共用复制后的图像，由本适配器唯一负责关闭 ImageProxy，最多一帧推理在途。 */
class PosePersonAnalyzer(
    context: Context,
    private val scope: CoroutineScope,
    private val sessionId: String,
    private val generation: Long,
    private val deliver: (Observation) -> Unit,
    private val onFailure: () -> Unit,
    private val onTransform: (OutputTransform, Int, Int) -> Unit,
) : ImageAnalysis.Analyzer {
    private val pose = PoseDetection.getClient(PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build())
    private val people = try { ObjectDetector.createFromOptions(context, ObjectDetector.ObjectDetectorOptions.builder()
        .setBaseOptions(BaseOptions.builder().setModelAssetPath("models/efficientdet_lite0.tflite").build())
        .setRunningMode(RunningMode.IMAGE).setScoreThreshold(.45f).setMaxResults(6)
        .setCategoryAllowlist(listOf("person")).build()) }
    catch (e: Exception) { pose.close(); throw e }
    val released = CompletableDeferred<Unit>()
    private val busy = AtomicBoolean(false)
    private val modelsClosed = AtomicBoolean(false)
    @Volatile private var closed = false
    private var lastNs = 0L
    private var diagnosticFormat: String? = null
    private var previousBorder: List<Float>? = null

    override fun analyze(image: ImageProxy) {
        if (closed || image.imageInfo.timestamp - lastNs < 200_000_000L || !busy.compareAndSet(false, true)) {
            image.close()
            return
        }
        lastNs = image.imageInfo.timestamp
        val timestamp = lastNs / 1_000
        val rotation = image.imageInfo.rotationDegrees
        val bitmap: Bitmap
        try {
            val shortEdge = if (image.width < image.height) image.width else image.height
            val longEdge = if (image.width > image.height) image.width else image.height
            check(shortEdge >= 480 && longEdge >= 640) { "Analysis resolution unsupported" }
            val format = "${image.width}x${image.height}:$rotation"
            if (diagnosticFormat != format) {
                logdNoFile(tag = TAG) { "analysis format sessionId=$sessionId generation=$generation width=${image.width} height=${image.height} rotationDegrees=$rotation sampleIntervalMs=200" }
                diagnosticFormat = format
            }
            val transform = ImageProxyTransformFactory().apply { isUsingRotationDegrees = true; isUsingCropRect = true }.getOutputTransform(image)
            onTransform(transform, if (rotation % 180 == 0) image.width else image.height, if (rotation % 180 == 0) image.height else image.width)
            val raw = image.toBitmap()
            bitmap = if (rotation == 0) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height,
                Matrix().apply { postRotate(rotation.toFloat()) }, true).also { if (it !== raw) raw.recycle() }
        } catch (e: Exception) {
            loge(tag = TAG) { "analysis input failed sessionId=$sessionId generation=$generation errorType=${e.javaClass.simpleName}" }
            busy.set(false)
            onFailure()
            return
        } finally { image.close() }
        scope.launch(Dispatchers.Default) {
            try {
                val result = suspendCancellableCoroutine<com.google.mlkit.vision.pose.Pose> { continuation ->
                    pose.process(InputImage.fromBitmap(bitmap, 0)).addOnSuccessListener {
                        if (continuation.isActive) continuation.resume(it)
                    }.addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
                }
                val input = BitmapImageBuilder(bitmap).build()
                val boxes = try {
                    people.detect(input).detections().map { detection ->
                        val box = detection.boundingBox()
                        SeatRegion(box.left / bitmap.width, box.top / bitmap.height, box.right / bitmap.width, box.bottom / bitmap.height)
                    }
                } finally { input.close() }
                val names = mapOf(PoseLandmark.NOSE to "nose", PoseLandmark.LEFT_EYE to "leftEye", PoseLandmark.RIGHT_EYE to "rightEye",
                    PoseLandmark.LEFT_EAR to "leftEar", PoseLandmark.RIGHT_EAR to "rightEar", PoseLandmark.LEFT_SHOULDER to "leftShoulder",
                    PoseLandmark.RIGHT_SHOULDER to "rightShoulder", PoseLandmark.LEFT_HIP to "leftHip", PoseLandmark.RIGHT_HIP to "rightHip")
                val landmarks = result.allPoseLandmarks.mapNotNull { point -> names[point.landmarkType]?.let {
                    it to Point(point.position.x / bitmap.width, point.position.y / bitmap.height, point.inFrameLikelihood)
                } }.toMap()
                val columns = 24
                val rows = 18
                val cells = ArrayList<SceneCell>(columns * rows)
                val border = mutableListOf<Float>()
                for (row in 0 until rows) for (column in 0 until columns) {
                    val samples = mutableListOf<Float>()
                    var redChroma = 0f
                    var blueChroma = 0f
                    for (y in 1..3) for (x in 1..3) {
                        val pixel = bitmap.getPixel((column * 4 + x) * bitmap.width / (columns * 4),
                            (row * 4 + y) * bitmap.height / (rows * 4))
                        val red = (pixel shr 16) and 255
                        val green = (pixel shr 8) and 255
                        val blue = pixel and 255
                        samples += red * .299f + green * .587f + blue * .114f
                        redChroma += red - green
                        blueChroma += blue - green
                    }
                    val mean = samples.average().toFloat()
                    val texture = kotlin.math.sqrt(samples.sumOf { ((it - mean) * (it - mean)).toDouble() } / samples.size).toFloat()
                    cells += SceneCell(mean, redChroma / samples.size, blueChroma / samples.size, texture)
                    if (row == 0 || row == rows - 1 || column == 0 || column == columns - 1) border += mean
                }
                val mean = cells.map { it.luminance }.average()
                val variance = cells.sumOf { (it.luminance - mean) * (it.luminance - mean) } / cells.size
                val moved = previousBorder?.zip(border)?.count { (a, b) -> abs(a - b) > 60 }?.let { it > border.size * .65 } == true
                previousBorder = border
                if (!closed) deliver(Observation(sessionId, generation, timestamp, rotation, landmarks, boxes,
                    sceneClear = mean in 25.0..235.0 && variance > 100, deviceMoved = moved,
                    sceneGrid = SceneGrid(columns, rows, cells)))
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { if (!closed) onFailure() }
            finally {
                bitmap.recycle()
                busy.set(false)
                if (closed) releaseModels()
            }
        }
    }
    fun close() {
        closed = true
        if (!busy.get()) releaseModels()
    }
    private fun releaseModels() {
        if (modelsClosed.compareAndSet(false, true)) {
            try { try { pose.close() } finally { people.close() }; released.complete(Unit) }
            catch (e: Exception) { released.completeExceptionally(e) }
        }
    }
}
