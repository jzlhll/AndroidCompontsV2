package childmonitor.platform

import childmonitor.TAG
import com.au.module_android.log.logdNoFile
import com.au.module_android.log.loge
import android.annotation.SuppressLint
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.SystemClock
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.impl.ConstantObservable
import androidx.camera.core.impl.Observable
import androidx.camera.core.impl.Timebase
import androidx.camera.video.Recorder
import java.io.File
import childmonitor.model.DefaultMonitorConfig
import java.util.concurrent.Executor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds

/** 在编码样本写入时固定媒体起点，录像始终只有视频轨道。 */
@SuppressLint("RestrictedApi")
class TimestampedVideoOutput(
    specification: Recorder,
    private val output: File,
    private val scope: CoroutineScope,
    private val executor: Executor,
    private val sessionId: String,
) : RecorderVideoOutput(specification) {
    val firstSampleUs = CompletableDeferred<Long>()
    val finalized = CompletableDeferred<Unit>()
    val surfaceReleased = CompletableDeferred<Unit>()
    val resourcesReleased = CompletableDeferred<Unit>()
    @Volatile private var codec: MediaCodec? = null
    @Volatile private var ending = false
    private var requested = false

    override fun isSourceStreamRequired(): Observable<Boolean> = ConstantObservable.withValue(true)
    override fun onSurfaceRequested(request: SurfaceRequest) {
        request.willNotProvideSurface()
        firstSampleUs.completeExceptionally(IllegalStateException("Missing video timebase"))
    }
    override fun onSurfaceRequested(request: SurfaceRequest, timebase: Timebase, hasGlProcessing: Boolean) {
        if (ending) {
            request.willNotProvideSurface()
            surfaceReleased.complete(Unit)
            return
        }
        if (requested) {
            request.willNotProvideSurface()
            val failure = IllegalStateException("Video surface changed")
            firstSampleUs.completeExceptionally(failure)
            finalized.completeExceptionally(failure)
            ending = true
            return
        }
        requested = true
        val rotation = CompletableDeferred<Int>()
        request.setTransformationInfoListener(executor) { rotation.complete(it.rotationDegrees) }
        scope.launch(Dispatchers.IO) {
            var encoder: MediaCodec? = null
            var muxer: MediaMuxer? = null
            var input: android.view.Surface? = null
            var muxerStarted = false
            var track = -1
            var originPts: Long? = null
            var sampleCount = 0L
            var lastPtsUs = 0L
            var failure: Throwable? = null
            try {
                val size = request.resolution
                check(ProbeMediaInspector.isSupportedSize(size.width, size.height))
                val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, size.width, size.height).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                    setInteger(MediaFormat.KEY_BIT_RATE, DefaultMonitorConfig.videoBitRate)
                    setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                    setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0)
                }
                encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                input = encoder.createInputSurface()
                muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val rotationDegrees = withTimeout(3_000.milliseconds) { rotation.await() }
                muxer.setOrientationHint(rotationDegrees)
                logdNoFile(tag = TAG) { "encoder configured sessionId=$sessionId width=${size.width} height=${size.height} rotationDegrees=$rotationDegrees timebase=$timebase glProcessing=$hasGlProcessing bitRate=${DefaultMonitorConfig.videoBitRate} audioTracks=0" }
                encoder.start()
                codec = encoder
                request.provideSurface(input, executor) { surfaceReleased.complete(Unit) }
                val info = MediaCodec.BufferInfo()
                var eosSignaled = false
                while (isActive) {
                    if (ending && !eosSignaled) { encoder.signalEndOfInputStream(); eosSignaled = true }
                    val index = encoder.dequeueOutputBuffer(info, 10_000)
                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        check(!muxerStarted)
                        track = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    } else if (index >= 0) {
                        val buffer = checkNotNull(encoder.getOutputBuffer(index))
                        if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            check(muxerStarted)
                            val absolutePts = info.presentationTimeUs
                            if (originPts == null) {
                                originPts = absolutePts
                                val offset = when (timebase) {
                                    Timebase.REALTIME -> 0L
                                    Timebase.UPTIME -> SystemClock.elapsedRealtimeNanos() / 1_000 - System.nanoTime() / 1_000
                                }
                                info.presentationTimeUs = 0
                                muxer.writeSampleData(track, buffer, info)
                                firstSampleUs.complete(absolutePts + offset)
                                logdNoFile(tag = TAG) { "encoder first sample sessionId=$sessionId sourcePtsUs=$absolutePts clockOffsetUs=$offset originUs=${absolutePts + offset} mediaPtsUs=0" }
                            } else {
                                info.presentationTimeUs = absolutePts - checkNotNull(originPts)
                                check(info.presentationTimeUs >= 0)
                                muxer.writeSampleData(track, buffer, info)
                            }
                        }
                        if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            sampleCount++
                            lastPtsUs = info.presentationTimeUs
                        }
                        val done = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        encoder.releaseOutputBuffer(index, false)
                        if (done) break
                    }
                }
                check(originPts != null)
            } catch (e: Exception) {
                failure = e
                firstSampleUs.completeExceptionally(e)
                if (input == null) request.willNotProvideSurface()
            } finally {
                codec = null
                withContext(NonCancellable) {
                    try { if (muxerStarted) muxer?.stop() } catch (e: Exception) { failure = failure ?: e }
                    try { muxer?.release() } catch (e: Exception) { failure = failure ?: e }
                    try { encoder?.stop() } catch (_: Exception) { }
                    try { encoder?.release() } catch (e: Exception) { failure = failure ?: e }
                    try { input?.release() } catch (e: Exception) { failure = failure ?: e }
                    resourcesReleased.complete(Unit)
                    logdNoFile(tag = TAG) { "encoder finished sessionId=$sessionId success=${failure == null} samples=$sampleCount lastMediaPtsUs=$lastPtsUs surfaceReleased=${surfaceReleased.isCompleted}" }
                    failure?.let { error -> loge(tag = TAG) { "encoder failed sessionId=$sessionId samples=$sampleCount errorType=${error.javaClass.simpleName}" } }
                    if (failure == null) finalized.complete(Unit) else finalized.completeExceptionally(checkNotNull(failure))
                }
            }
        }
    }
    fun stop() {
        ending = true
        if (!requested) {
            val failure = IllegalStateException("Video surface unavailable")
            firstSampleUs.completeExceptionally(failure)
            finalized.completeExceptionally(failure)
            resourcesReleased.complete(Unit)
            surfaceReleased.complete(Unit)
        }
    }
}
