package com.allan.mydroid.views.send

import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import com.allan.mydroid.R
import com.allan.mydroid.beansinner.ShareInBean
import com.allan.mydroid.databinding.ViewSendMediaPreviewBinding
import com.au.module_android.Globals
import com.au.module_android.click.onClick
import com.au.module_android.glide.clearByGlide
import com.au.module_android.glide.glideSetAny
import com.au.module_android.log.logEx
import com.au.module_android.utils.dp
import com.au.module_android.utils.gone
import com.au.module_android.utils.visible
import com.bumptech.glide.load.engine.DiskCacheStrategy
import eightbitlab.com.blurview.BlurTarget
import eightbitlab.com.blurview.BlurView3Util
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds

/** 发送列表共用的全屏媒体预览，管理模糊背景、视频播放与可拖动的帧操作按钮组。 */
@androidx.annotation.OptIn(UnstableApi::class)
class SendMediaPreview(
    fragment: Fragment,
    private val binding: ViewSendMediaPreviewBinding,
    private val blurTarget: BlurTarget,
) : DefaultLifecycleObserver {
    private val scope = fragment.viewLifecycleOwner.lifecycleScope
    private var player: ExoPlayer? = null
    private var progressJob: Job? = null
    private var centerControlsVisible = true
    private val centerControls: View = binding.videoPlayer.findViewById(androidx.media3.ui.R.id.exo_center_controls)
    private val controlsBackground: View = binding.videoPlayer.findViewById(androidx.media3.ui.R.id.exo_controls_background)
    private val frameButtons = listOf(binding.backTenFramesBtn, binding.stepFrameBtn, binding.nextTenFramesBtn)
    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            dismiss()
        }
    }

    init {
        fragment.viewLifecycleOwner.lifecycle.addObserver(this)
        fragment.requireActivity().onBackPressedDispatcher.addCallback(fragment.viewLifecycleOwner, backCallback)
        BlurView3Util(binding.backdropBlurView, 0, 20f.dp).setBlurWithOverlayNoNoise(
            blurTarget,
            0x99000000.toInt(),
            0xE6000000.toInt(),
        )
        binding.root.onClick(0) {
            if (player == null) dismiss() else togglePausedControls()
        }
        binding.previewImg.onClick { dismiss() }
        binding.closePreviewBtn.onClick { dismiss() }
        binding.videoPlayer.onClick(0) { togglePausedControls() }
        binding.stepFrameBtn.onClick(0) { stepFrames(1) }
        binding.nextTenFramesBtn.onClick(0) { stepFrames(10) }
        binding.backTenFramesBtn.onClick(0) { stepFrames(-10) }
        binding.mediaHost.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            constrainFrameControls()
        }
        frameButtons.forEach { initFrameButtonDrag(it) }
    }

    fun updateInsets(statusBarHeight: Int, navigationBarHeight: Int) {
        binding.mediaHost.updateLayoutParams<FrameLayout.LayoutParams> {
            topMargin = statusBarHeight + 16.dp
            bottomMargin = navigationBarHeight + 32.dp
        }
    }

    fun show(bean: ShareInBean, isVideo: Boolean) {
        dismiss()
        binding.root.visible()
        backCallback.isEnabled = true
        blurTarget.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        binding.closePreviewBtn.requestFocus()
        if (!isVideo) {
            binding.previewImg.visible()
            // 按预览区域解码，避免全尺寸大图占用过多内存。
            binding.previewImg.glideSetAny(bean.uri) {
                it.fitCenter().diskCacheStrategy(DiskCacheStrategy.NONE)
            }
            return
        }

        binding.videoPlayer.visible()
        binding.videoTimeTv.visible()
        binding.frameControlsHost.visible()
        binding.frameControlsHost.translationX = 0f
        binding.frameControlsHost.translationY = 0f
        centerControlsVisible = true
        val videoPlayer = ExoPlayer.Builder(binding.root.context).build()
        player = videoPlayer
        binding.videoPlayer.player = videoPlayer
        videoPlayer.setSeekParameters(SeekParameters.EXACT)
        videoPlayer.setAudioAttributes(
            androidx.media3.common.AudioAttributes.DEFAULT,
            true,
        )
        videoPlayer.setHandleAudioBecomingNoisy(true)
        videoPlayer.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                updateVideoProgress()
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                constrainFrameControls()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (playWhenReady) centerControlsVisible = true
            }

            override fun onPlayerError(error: PlaybackException) {
                logEx(throwable = error) { "Preview video playback failed" }
                binding.videoPlayer.setCustomErrorMessage(Globals.app.getString(R.string.preview_video_error))
                updateVideoProgress()
            }
        })
        binding.videoPlayer.setCustomErrorMessage(null)
        videoPlayer.setMediaItem(MediaItem.fromUri(bean.uri))
        videoPlayer.prepare()
        videoPlayer.play()
        binding.videoPlayer.showController()
        progressJob = scope.launch {
            while (isActive) {
                updateVideoProgress()
                delay(100.milliseconds)
            }
        }
    }

    private fun stepFrames(frameCount: Int) {
        val currentPlayer = player ?: return
        val duration = currentPlayer.duration
        val canSeek = currentPlayer.playbackState == Player.STATE_READY ||
                currentPlayer.playbackState == Player.STATE_ENDED
        if (!canSeek ||
            !currentPlayer.isCurrentMediaItemSeekable || currentPlayer.playerError != null ||
            duration == C.TIME_UNSET || duration <= 0) return
        if (frameCount > 0 && currentPlayer.currentPosition >= duration - 1) return
        if (frameCount < 0 && currentPlayer.currentPosition <= 0) return
        currentPlayer.pause()
        val frameRate = currentPlayer.videoFormat?.frameRate ?: -1f
        // 精确 seek 配合帧率做小步前进；缺少帧率或可变帧率视频不保证严格逐帧。
        val interval = if (frameRate.isFinite() && frameRate > 0f) {
            max(1L, (1000.0 / frameRate).roundToLong())
        } else {
            33L
        }
        currentPlayer.seekTo(max(0L, min(currentPlayer.currentPosition + interval * frameCount, duration - 1)))
        updateVideoProgress()
    }

    private fun togglePausedControls() {
        val currentPlayer = player ?: return
        if (currentPlayer.playWhenReady) return
        centerControlsVisible = !centerControlsVisible
        updateVideoProgress()
    }

    private fun updateVideoProgress() {
        val currentPlayer = player ?: return
        val duration = currentPlayer.duration
        val hasDuration = duration != C.TIME_UNSET && duration > 0
        // seekTo 会立即进入 BUFFERING；等待 READY 后再允许下一步，避免连续跳转相互干扰。
        val canStep = hasDuration && currentPlayer.playerError == null &&
                (currentPlayer.playbackState == Player.STATE_READY || currentPlayer.playbackState == Player.STATE_ENDED) &&
                currentPlayer.isCurrentMediaItemSeekable
        val canStepForward = canStep && currentPlayer.currentPosition < duration - 1
        binding.stepFrameBtn.isEnabled = canStepForward
        binding.nextTenFramesBtn.isEnabled = canStepForward
        binding.backTenFramesBtn.isEnabled = canStep && currentPlayer.currentPosition > 0
        // 仅隐藏中央播放控件和遮罩，帧操作按钮、时间与进度条保持显示。
        val visibility = if (currentPlayer.playWhenReady || centerControlsVisible) View.VISIBLE else View.INVISIBLE
        centerControls.visibility = visibility
        controlsBackground.visibility = visibility
        binding.videoPlayer.keepScreenOn = currentPlayer.isPlaying
        binding.videoTimeTv.text = Globals.app.getString(
            R.string.preview_video_progress,
            formatTime(currentPlayer.currentPosition),
            if (hasDuration) formatTime(duration) else "--:--.---",
        )
    }

    private fun formatTime(timeMs: Long): String {
        val milliseconds = max(0L, timeMs)
        return String.format(
            Locale.getDefault(), "%02d:%02d:%02d.%03d",
            milliseconds / 3_600_000,
            milliseconds / 60_000 % 60,
            milliseconds / 1000 % 60,
            milliseconds % 1000,
        )
    }

    private fun initFrameButtonDrag(frameButton: View) {
        val controls = binding.frameControlsHost
        val touchSlop = ViewConfiguration.get(binding.root.context).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var initialX = 0f
        var initialY = 0f
        var dragging = false
        frameButton.setOnTouchListener { button, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    initialX = controls.x
                    initialY = controls.y
                    dragging = false
                    button.isPressed = true
                    button.parent.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        dragging = true
                        button.isPressed = false
                    }
                    if (dragging) {
                        controls.x = initialX + dx
                        controls.y = initialY + dy
                        constrainFrameControls()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    button.isPressed = false
                    button.parent.requestDisallowInterceptTouchEvent(false)
                    if (!dragging) button.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    button.isPressed = false
                    button.parent.requestDisallowInterceptTouchEvent(false)
                    true
                }
                else -> false
            }
        }
    }

    private fun constrainFrameControls() {
        val controls = binding.frameControlsHost
        val host = binding.mediaHost
        if (controls.width == 0 || host.width == 0 || host.height == 0) return
        val bounds = RectF(0f, 0f, host.width.toFloat(), host.height.toFloat())
        val size = player?.videoSize
        if (size != null && size.width > 0 && size.height > 0) {
            val ratio = size.width * size.pixelWidthHeightRatio / size.height
            if (ratio > 0f) {
                if (host.width.toFloat() / host.height > ratio) {
                    val width = host.height * ratio
                    bounds.left = (host.width - width) / 2f
                    bounds.right = bounds.left + width
                } else {
                    val height = host.width / ratio
                    bounds.top = (host.height - height) / 2f
                    bounds.bottom = bounds.top + height
                }
            }
        }
        // 避开顶部关闭按钮和底部播放进度条，窄视频允许按钮组略超出画面。
        val left = min(bounds.left, max(0f, host.width - controls.width.toFloat()))
        val right = max(left, bounds.right - controls.width)
        val top = min(max(bounds.top, 60f.dp), max(0f, host.height - controls.height.toFloat()))
        val bottom = max(top, min(bounds.bottom, host.height - 96f.dp) - controls.height)
        controls.x = max(left, min(controls.x, right))
        controls.y = max(top, min(controls.y, bottom))
    }

    fun dismiss() {
        backCallback.isEnabled = false
        progressJob?.cancel()
        progressJob = null
        binding.videoPlayer.player = null
        player?.release()
        player = null
        binding.videoPlayer.keepScreenOn = false
        binding.previewImg.clearByGlide()
        binding.previewImg.setImageDrawable(null)
        binding.previewImg.gone()
        binding.videoPlayer.gone()
        binding.videoTimeTv.gone()
        binding.frameControlsHost.gone()
        binding.root.gone()
        blurTarget.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
    }

    override fun onStop(owner: LifecycleOwner) {
        dismiss()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        dismiss()
    }
}
