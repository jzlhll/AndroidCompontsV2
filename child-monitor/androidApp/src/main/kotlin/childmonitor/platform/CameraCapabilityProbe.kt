package childmonitor.platform

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import childmonitor.android.BuildConfig
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.io.File
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/** Debug 采集能力探针：临时文件独立于正式会话，终止时等待封装、解绑并清理可释放文件。 */
class CameraCapabilityProbe(context: Context, private val permit: Semaphore) {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableStateFlow = MutableStateFlow(ProbeState())
    val stateFlow = mutableStateFlow.asStateFlow()
    private var job: Job? = null
    private var disposed = false

    @SuppressLint("MissingPermission")
    fun start(owner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider, rotation: Int) {
        if (!BuildConfig.DEBUG || disposed || job?.isActive == true || !stateFlow.value.captureReleased) return
        val generation = stateFlow.value.generation + 1
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            mutableStateFlow.value = ProbeState(generation, ProbePhase.Failed, error = ProbeError.PermissionDenied)
            return
        }
        if (!permit.tryAcquire()) {
            mutableStateFlow.value = ProbeState(generation, ProbePhase.Failed, error = ProbeError.CameraBusy)
            return
        }
        mutableStateFlow.value = ProbeState(generation, ProbePhase.Starting, captureReleased = false)
        job = scope.launch {
            var provider: ProcessCameraProvider? = null
            var camera: Camera? = null
            var preview: Preview? = null
            var analysis: ImageAnalysis? = null
            var video: VideoCapture<Recorder>? = null
            var detector: PoseDetector? = null
            var recording: Recording? = null
            var file: File? = null
            val executor = Executors.newSingleThreadExecutor()
            val mainExecutor = ContextCompat.getMainExecutor(context)
            val started = CompletableDeferred<Unit>()
            val finalized = CompletableDeferred<VideoRecordEvent.Finalize>()
            var acceptingAnalysis = true
            var recordingWindow = false
            var frames = 0
            var poseFrames = 0
            var firstFrameNs = 0L
            var lastFrameNs = 0L
            var startCallbackNs = 0L
            var result: ProbeReport? = null
            var failure: ProbeError? = null
            var canceled = false
            try {
                withTimeout(15_000.milliseconds) {
                    val future = ProcessCameraProvider.getInstance(context)
                    provider = suspendCancellableCoroutine { continuation ->
                        future.addListener({
                            if (continuation.isActive) {
                                try { continuation.resume(future.get()) }
                                catch (e: Exception) { continuation.resumeWithException(e) }
                            }
                        }, mainExecutor)
                    }
                    val currentProvider = checkNotNull(provider)
                    if (!currentProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                        throw ProbeException(ProbeError.NoFrontCamera)
                    }
                    val info = currentProvider.getCameraInfo(CameraSelector.DEFAULT_FRONT_CAMERA)
                    val size = QualitySelector.getResolution(info, Quality.SD)
                    if (size == null || !ProbeMediaInspector.isSupportedSize(size.width, size.height)) {
                        throw ProbeException(ProbeError.UnsupportedSize)
                    }
                    val poseDetector = PoseDetection.getClient(
                        PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build(),
                    )
                    detector = poseDetector
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setTargetRotation(rotation)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setResolutionSelector(
                            ResolutionSelector.Builder().setResolutionStrategy(
                                ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                            ).build(),
                        ).build()
                    analysis = imageAnalysis
                    imageAnalysis.setAnalyzer(executor, MlKitAnalyzer(
                        listOf(poseDetector), ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL, mainExecutor,
                    ) { observation ->
                        if (acceptingAnalysis && recordingWindow && generation == stateFlow.value.generation) {
                            if (observation.getThrowable(poseDetector) != null) {
                                started.completeExceptionally(ProbeException(ProbeError.ModelFailed))
                                failure = ProbeError.ModelFailed
                            } else {
                                val timeNs = observation.timestamp
                                if (timeNs > lastFrameNs) {
                                    if (frames == 0) firstFrameNs = timeNs
                                    lastFrameNs = timeNs
                                    frames++
                                    if (observation.getValue(poseDetector)?.allPoseLandmarks?.isNotEmpty() == true) poseFrames++
                                    mutableStateFlow.value = stateFlow.value.copy(analysisFrames = frames, poseFrames = poseFrames)
                                }
                            }
                        }
                    })
                    val previewUseCase = Preview.Builder().setTargetRotation(rotation).build()
                    preview = previewUseCase
                    previewUseCase.setSurfaceProvider(surfaceProvider)
                    val recorder = Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.SD)).build()
                    val videoUseCase = VideoCapture.withOutput(recorder).also { it.targetRotation = rotation }
                    video = videoUseCase
                    camera = currentProvider.bindToLifecycle(
                        owner, CameraSelector.DEFAULT_FRONT_CAMERA, previewUseCase, videoUseCase, imageAnalysis,
                    )
                    val analysisSize = imageAnalysis.resolutionInfo?.resolution
                    if (analysisSize == null || (analysisSize.width < 480 && analysisSize.height < 480) ||
                        analysisSize.width < 360 || analysisSize.height < 360) {
                        throw ProbeException(ProbeError.UnsupportedSize)
                    }
                    file = withContext(Dispatchers.IO) {
                        val directory = File(context.cacheDir, "capability-probes")
                        check(directory.isDirectory || directory.mkdirs())
                        // 这里只包含能力探针临时文件，进程中断后的遗留内容不进入正式记录。
                        directory.listFiles()?.forEach { stale -> check(stale.delete()) }
                        File.createTempFile("probe-", ".mp4", directory)
                    }
                    recording = recorder.prepareRecording(context, FileOutputOptions.Builder(checkNotNull(file)).build())
                        .start(mainExecutor) { event ->
                            when (event) {
                                is VideoRecordEvent.Start -> {
                                    startCallbackNs = SystemClock.elapsedRealtimeNanos()
                                    recordingWindow = true
                                    started.complete(Unit)
                                }
                                is VideoRecordEvent.Finalize -> {
                                    recordingWindow = false
                                    finalized.complete(event)
                                    if (!started.isCompleted) started.completeExceptionally(ProbeException(ProbeError.FinalizeFailed))
                                }
                            }
                        }
                    started.await()
                }
                mutableStateFlow.value = stateFlow.value.copy(phase = ProbePhase.Recording)
                delay(5_000.milliseconds)
                if (frames < 2 || lastFrameNs - firstFrameNs < 1_000_000_000L) {
                    throw ProbeException(ProbeError.NoAnalysis)
                }
            } catch (e: TimeoutCancellationException) {
                failure = ProbeError.Timeout
            } catch (e: CancellationException) {
                canceled = true
                throw e
            } catch (e: ProbeException) {
                failure = e.error
            } catch (_: Exception) {
                failure = ProbeError.CaptureFailed
            } finally {
                acceptingAnalysis = false
                mutableStateFlow.value = stateFlow.value.copy(phase = ProbePhase.Finishing)
                withContext(NonCancellable) {
                    analysis?.clearAnalyzer()
                    var finalEvent: VideoRecordEvent.Finalize? = null
                    try {
                        recording?.stop()
                        if (recording != null) {
                            finalEvent = withTimeoutOrNull(10_000.milliseconds) { finalized.await() }
                            if (finalEvent == null || finalEvent.hasError()) failure = failure ?: ProbeError.FinalizeFailed
                        }
                    } catch (_: Exception) {
                        failure = failure ?: ProbeError.FinalizeFailed
                    }
                    var released = camera == null
                    val currentCamera = camera
                    val closed = CompletableDeferred<Unit>()
                    val observer = Observer<CameraState> { state ->
                        if (state.type == CameraState.Type.CLOSED) closed.complete(Unit)
                    }
                    try {
                        val useCases = listOfNotNull(preview, video, analysis).toTypedArray()
                        provider?.unbind(*useCases)
                        if (currentCamera != null) {
                            currentCamera.cameraInfo.cameraState.observeForever(observer)
                            released = withTimeoutOrNull(10_000.milliseconds) { closed.await(); true } == true
                        }
                    } catch (_: Exception) {
                        released = false
                    } finally {
                        currentCamera?.cameraInfo?.cameraState?.removeObserver(observer)
                        preview?.setSurfaceProvider(null)
                        try { detector?.close() } catch (_: Exception) { failure = failure ?: ProbeError.ModelFailed }
                        executor.shutdown()
                    }
                    val output = file
                    if (!canceled && failure == null && output != null && finalEvent != null) {
                        try {
                            val media = withContext(Dispatchers.IO) { ProbeMediaInspector.read(output) }
                            result = ProbeReport(media.width, media.height, media.firstPtsUs, media.lastPtsUs,
                                firstFrameNs, lastFrameNs, startCallbackNs, frames, poseFrames)
                        } catch (_: Exception) {
                            failure = ProbeError.InvalidMedia
                        }
                    }
                    // 未收到封装结果时不删除仍可能被写入的文件，下次探针开始前清理。
                    if (output != null && (recording == null || finalEvent != null)) {
                        val deleted = try {
                            withContext(Dispatchers.IO) { !output.exists() || output.delete() }
                        } catch (_: Exception) { false }
                        if (!deleted) failure = failure ?: ProbeError.CleanupFailed
                    }
                    // 封装超时也保留许可，避免新页面清理仍在写入的旧临时文件。
                    released = released && (recording == null || finalEvent != null)
                    if (released) permit.release()
                    mutableStateFlow.value = stateFlow.value.copy(
                        phase = when {
                            failure != null || !released -> ProbePhase.Failed
                            canceled -> ProbePhase.Canceled
                            result != null -> ProbePhase.Complete
                            else -> ProbePhase.Failed
                        },
                        error = failure,
                        captureReleased = released,
                        report = result,
                    )
                    if (disposed) scope.cancel()
                }
            }
        }
    }

    fun stop() { job?.cancel() }

    fun close() {
        disposed = true
        if (job?.isActive == true) job?.cancel() else scope.cancel()
    }
}

private class ProbeException(val error: ProbeError) : Exception(error.name)
