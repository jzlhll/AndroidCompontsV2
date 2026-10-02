package childmonitor.platform

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.view.Surface
import android.graphics.RectF
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.OutputTransform
import childmonitor.model.SeatRegion
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import childmonitor.model.EndReason
import childmonitor.model.Observation
import childmonitor.storage.AndroidFileStorage
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore

/** 可撤销的前台相机绑定；业务层不持有 Activity 或 PreviewView。 */
class AndroidCaptureAdapter(
    context: Context,
    private val files: AndroidFileStorage,
    private val scope: CoroutineScope,
    private val permit: Semaphore,
) : CaptureAdapter {
    private val context = context.applicationContext
    private var owner: LifecycleOwner? = null
    private var previewView: PreviewView? = null
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var stateObserver: Observer<CameraState>? = null
    private var preview: Preview? = null
    private var analysis: ImageAnalysis? = null
    private var video: VideoCapture<TimestampedVideoOutput>? = null
    private var output: TimestampedVideoOutput? = null
    private var analyzer: PosePersonAnalyzer? = null
    @Volatile private var frameTransform: OutputTransform? = null
    @Volatile private var frameWidth = 0
    @Volatile private var frameHeight = 0
    private var ownsPermit = false
    private val executor = Executors.newSingleThreadExecutor()

    @SuppressLint("UnsafeOptInUsageError")
    fun mapPlacement(rect: RectF): SeatRegion? {
        val target = frameTransform ?: return null
        val source = previewView?.outputTransform ?: return null
        if (frameWidth <= 0 || frameHeight <= 0) return null
        return try {
            val mapped = RectF(rect)
            CoordinateTransform(source, target).mapRect(mapped)
            SeatRegion(mapped.left / frameWidth, mapped.top / frameHeight, mapped.right / frameWidth, mapped.bottom / frameHeight).also { it.validate() }
        } catch (_: Exception) { null }
    }

    fun attach(owner: LifecycleOwner, view: PreviewView) { this.owner = owner; previewView = view }
    fun detach(view: PreviewView) {
        if (previewView === view) { owner = null; previewView = null }
    }

    @SuppressLint("MissingPermission", "UnsafeOptInUsageError")
    override suspend fun start(sessionId: String, generation: Long, stagingPath: String,
        onObservation: (Observation) -> Unit, onInterrupted: (EndReason) -> Unit): Long = withContext(Dispatchers.Main.immediate) {
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        check(!ownsPermit && permit.tryAcquire()) { "Camera busy" }
        ownsPermit = true
        val lifecycle = checkNotNull(owner) { "Preview detached" }
        val view = checkNotNull(previewView)
        val main = ContextCompat.getMainExecutor(context)
        val future = ProcessCameraProvider.getInstance(context)
        provider = suspendCancellableCoroutine { continuation -> future.addListener({
            if (continuation.isActive) try { continuation.resume(future.get()) }
            catch (e: Exception) { continuation.resumeWithException(e) }
        }, main) }
        val cameraProvider = checkNotNull(provider)
        val info = cameraProvider.getCameraInfo(CameraSelector.DEFAULT_FRONT_CAMERA)
        // 未知时钟源不能凭到达时间猜测媒体映射，因此该设备组合明确拒绝启动。
        check(Camera2CameraInfo.from(info).getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE) ==
            CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME) { "Unsupported camera timestamp source" }
        val size = QualitySelector.getResolution(info, Quality.SD)
        check(size != null && ProbeMediaInspector.isSupportedSize(size.width, size.height))
        val rotation = view.display?.rotation ?: Surface.ROTATION_0
        val specification = Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.SD)).build()
        val encoder = TimestampedVideoOutput(specification, files.resolve(stagingPath), scope, main)
        output = encoder
        preview = Preview.Builder().setTargetRotation(rotation).build().also { it.setSurfaceProvider(view.surfaceProvider) }
        var created: PosePersonAnalyzer? = null
        try {
            analyzer = withContext(Dispatchers.Default) {
                PosePersonAnalyzer(context, scope, sessionId, generation, onObservation,
                    onFailure = { onInterrupted(EndReason.CaptureFailed) },
                    onTransform = { transform, width, height -> frameWidth = width; frameHeight = height; frameTransform = transform })
                    .also { created = it }
            }
        } finally {
            // withContext 取消时可能丢弃返回值，已构造的模型仍必须有关闭路径。
            if (created !== analyzer) {
                analyzer = created
                created?.close()
            }
        }
        analysis = ImageAnalysis.Builder().setTargetRotation(rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(
                ResolutionStrategy(android.util.Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build())
            .build().also { it.setAnalyzer(executor, checkNotNull(analyzer)) }
        video = VideoCapture.withOutput(encoder).also { it.targetRotation = rotation }
        camera = cameraProvider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_FRONT_CAMERA,
            checkNotNull(preview), checkNotNull(video), checkNotNull(analysis))
        var opened = false
        stateObserver = Observer<CameraState> { state ->
            if (state.type == CameraState.Type.OPEN) opened = true
            if (state.error != null || opened && state.type in listOf(CameraState.Type.CLOSING, CameraState.Type.CLOSED)) {
                val locked = (context.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager).isKeyguardLocked
                onInterrupted(when {
                    locked -> EndReason.SystemLocked
                    !lifecycle.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) -> EndReason.Background
                    else -> EndReason.CameraInterrupted
                })
            }
        }.also { observer -> checkNotNull(camera).cameraInfo.cameraState.observeForever(observer) }
        scope.launch {
            try { encoder.finalized.await() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { onInterrupted(EndReason.CaptureFailed) }
        }
        encoder.firstSampleUs.await()
    }

    override suspend fun stop(): CaptureFinish = withContext(Dispatchers.Main.immediate) {
        if (!ownsPermit) return@withContext CaptureFinish(true, false)
        analysis?.clearAnalyzer()
        analyzer?.close()
        val current = camera
        stateObserver?.let { current?.cameraInfo?.cameraState?.removeObserver(it) }; stateObserver = null
        val closed = CompletableDeferred<Unit>()
        val observer = Observer<CameraState> { if (it.type == CameraState.Type.CLOSED) closed.complete(Unit) }
        var released = current == null
        var finalized = false
        try {
            provider?.unbind(*listOfNotNull(preview, analysis, video).toTypedArray())
            if (current != null) {
                current.cameraInfo.cameraState.observeForever(observer)
                released = withTimeoutOrNull(10_000.milliseconds) { closed.await(); true } == true
            }
            output?.stop()
            finalized = if (output == null) false else try {
                withTimeout(15_000.milliseconds) { checkNotNull(output).finalized.await() }
                true
            } catch (e: TimeoutCancellationException) { false }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { false }
            val encoder = output
            if (encoder != null && (!encoder.resourcesReleased.isCompleted || !encoder.surfaceReleased.isCompleted)) released = false
            val model = analyzer
            if (model != null) {
                val modelReleased = try { withTimeout(3_000.milliseconds) { model.released.await() }; true }
                catch (e: TimeoutCancellationException) { false }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { false }
                if (!modelReleased) released = false
            }
        } finally {
            current?.cameraInfo?.cameraState?.removeObserver(observer)
            preview?.setSurfaceProvider(null)
            if (released) {
                permit.release(); ownsPermit = false
                camera = null; analysis = null; video = null; output = null; preview = null; analyzer = null
            }
        }
        CaptureFinish(released, finalized)
    }
}
