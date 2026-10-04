package childmonitor.platform

import childmonitor.TAG
import com.au.module_android.log.logdNoFile
import com.au.module_android.log.logEx
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import childmonitor.model.*
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
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

/** 人脸、人体与姿态模型共用复制后的图像，由本适配器唯一负责关闭 ImageProxy，最多一帧推理在途。 */
class PosePersonAnalyzer(
    context: Context,
    private val scope: CoroutineScope,
    private val sessionId: String,
    private val generation: Long,
    private val deliver: (Observation) -> Unit,
    private val onFailure: () -> Unit,
) : ImageAnalysis.Analyzer {
    private val pose = PoseDetection.getClient(PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build())
    private val people = try { ObjectDetector.createFromOptions(context, ObjectDetector.ObjectDetectorOptions.builder()
        .setBaseOptions(BaseOptions.builder().setModelAssetPath("models/efficientdet_lite0.tflite").build())
        .setRunningMode(RunningMode.IMAGE).setScoreThreshold(.45f).setMaxResults(20).build()) }
    catch (e: Exception) { pose.close(); throw e }
    private val faces = try { FaceDetection.getClient(FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL).build()) }
    catch (e: Exception) { try { pose.close() } finally { people.close() }; throw e }
    val released = CompletableDeferred<Unit>()
    private val busy = AtomicBoolean(false)
    private val modelsClosed = AtomicBoolean(false)
    @Volatile private var closed = false
    private var lastNs = 0L
    private var diagnosticFormat: String? = null
    private var previousBorder: List<Float>? = null
    private val featureSizes = mapOf(FaceContour.FACE to 36, FaceContour.LEFT_EYE to 16, FaceContour.RIGHT_EYE to 16,
        FaceContour.NOSE_BOTTOM to 3, FaceContour.UPPER_LIP_TOP to 11, FaceContour.LOWER_LIP_BOTTOM to 9)
    private val names = mapOf(PoseLandmark.NOSE to "nose", PoseLandmark.LEFT_EYE to "leftEye", PoseLandmark.RIGHT_EYE to "rightEye",
        PoseLandmark.LEFT_EAR to "leftEar", PoseLandmark.RIGHT_EAR to "rightEar", PoseLandmark.LEFT_SHOULDER to "leftShoulder",
        PoseLandmark.RIGHT_SHOULDER to "rightShoulder", PoseLandmark.LEFT_HIP to "leftHip", PoseLandmark.RIGHT_HIP to "rightHip")

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
            val raw = image.toBitmap()
            bitmap = if (rotation == 0) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height,
                Matrix().apply { postRotate(rotation.toFloat()) }, true).also { if (it !== raw) raw.recycle() }
        } catch (e: Exception) {
            logEx(tag = TAG, throwable = e) { "analysis input failed sessionId=$sessionId generation=$generation" }
            busy.set(false)
            onFailure()
            return
        } finally { image.close() }
        scope.launch(Dispatchers.Default) {
            var input: MPImage? = null
            try {
                val result = suspendCancellableCoroutine<com.google.mlkit.vision.pose.Pose> { continuation ->
                    pose.process(InputImage.fromBitmap(bitmap, 0)).addOnSuccessListener {
                        if (continuation.isActive) continuation.resume(it)
                    }.addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
                }
                val detectedFaces = suspendCancellableCoroutine<List<Face>> { continuation ->
                    faces.process(InputImage.fromBitmap(bitmap, 0)).addOnSuccessListener {
                        if (continuation.isActive) continuation.resume(it)
                    }.addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
                }.map { face ->
                    val box = face.boundingBox
                    val bounds = SeatRegion(box.left.toFloat() / bitmap.width, box.top.toFloat() / bitmap.height,
                        box.right.toFloat() / bitmap.width, box.bottom.toFloat() / bitmap.height)
                    val largeEnough = box.width() >= 200 && box.height() >= 200
                    val contoursComplete = featureSizes.all { (kind, size) ->
                        val points = face.getContour(kind)?.points.orEmpty()
                        points.size == size && points.all { Point(it.x / bitmap.width, it.y / bitmap.height).inFrame }
                    }
                    FaceObservation(bounds = bounds, complete = largeEnough && contoursComplete &&
                        listOf(bounds.left, bounds.top, bounds.right, bounds.bottom).all { it in .02f..0.98f } &&
                        face.headEulerAngleX.isFinite() && face.headEulerAngleZ.isFinite() && abs(face.headEulerAngleY) <= 35f,
                        pitch = face.headEulerAngleX, yaw = face.headEulerAngleY, largeEnough = largeEnough)
                }
                input = BitmapImageBuilder(bitmap).build()
                val objects = people.detect(input).detections().mapNotNull { detection ->
                    val category = detection.categories().maxByOrNull { it.score() }?.categoryName() ?: return@mapNotNull null
                    val box = detection.boundingBox()
                    DetectedObject(category, SeatRegion(box.left / bitmap.width, box.top / bitmap.height, box.right / bitmap.width, box.bottom / bitmap.height))
                }
                val boxes = objects.filter { it.category == "person" }.map { it.bounds }
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
                    sceneGrid = SceneGrid(columns, rows, cells), faces = detectedFaces, objects = objects.filter { it.category != "person" }))
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                logEx(tag = TAG, throwable = e) { "analysis frame failed sessionId=$sessionId generation=$generation timestampUs=$timestamp" }
                if (!closed) onFailure()
            }
            finally {
                try {
                    // MPImage 关闭时会回收原 Bitmap，必须等本帧所有读取完成后再释放。
                    if (input != null) input.close() else bitmap.recycle()
                } finally {
                    busy.set(false)
                    if (closed) releaseModels()
                }
            }
        }
    }
    fun close() {
        closed = true
        if (!busy.get()) releaseModels()
    }
    private fun releaseModels() {
        if (modelsClosed.compareAndSet(false, true)) {
            try { try { pose.close() } finally { try { people.close() } finally { faces.close() } }; released.complete(Unit) }
            catch (e: Exception) { released.completeExceptionally(e) }
        }
    }
}
