package com.allan.mydroid.views.send

import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.doOnLayout
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.PlayerView
import com.allan.mydroid.R
import com.allan.mydroid.beansinner.ShareInBean
import com.allan.mydroid.databinding.ViewSendMediaPreviewBinding
import com.au.module_android.Globals
import com.au.module_android.click.onClick
import com.au.module_android.glide.clearByGlide
import com.au.module_android.log.logEx
import com.au.module_android.log.loge
import com.au.module_android.utils.dp
import com.au.module_android.utils.getScreenFullSize
import com.au.module_android.utils.gone
import com.au.module_android.utils.visible
import com.au.module_androidui.toast.ToastBuilder
import com.au.module_androidui.widget.FullScreenBitmapPreviewView
import com.au.module_gson.mmkv.mmkvGetInt
import com.au.module_gson.mmkv.mmkvSetAny
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import eightbitlab.com.blurview.BlurTarget
import eightbitlab.com.blurview.BlurView3Util
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds

/** 发送列表共用的媒体预览，图片使用顶层 Bitmap 预览，视频管理模糊背景、缩放及可记忆位置的播放和帧操作按钮组。 */
@androidx.annotation.OptIn(UnstableApi::class)
class SendMediaPreview(
    fragment: Fragment,
    private val binding: ViewSendMediaPreviewBinding,
    private val blurTarget: BlurTarget,
) : DefaultLifecycleObserver {
    private val activity = fragment.requireActivity()
    private val scope = fragment.viewLifecycleOwner.lifecycleScope
    private var imagePreview: FullScreenBitmapPreviewView? = null
    private var imageSource: ImageView? = null
    private var player: ExoPlayer? = null
    private var progressJob: Job? = null
    private var settingsPopup: PopupMenu? = null
    private val zoomPercents = listOf(100, 110, 120, 135, 150, 175, 200)
    private val zoomPreferenceKey = "preview_video_zoom_percent"
    private var zoomPercent = 100
    private val playbackSettingsBtn: View = binding.videoPlayer.findViewById(androidx.media3.ui.R.id.exo_settings)
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
            0x44000000,
            0x88000000.toInt(),
        )
        binding.videoPlayer.findViewById<View>(androidx.media3.ui.R.id.exo_controls_background)
            .setBackgroundColor(0x22000000)
        val centerControls = binding.videoPlayer.findViewById<ViewGroup>(androidx.media3.ui.R.id.exo_center_controls)
        (centerControls.parent as ViewGroup).removeView(centerControls)
        val buttonScale = 0.8f
        val verticalPadding = (6.dp * buttonScale).roundToInt()
        centerControls.setPadding(6.dp, verticalPadding, 6.dp, verticalPadding)
        for (index in 0 until centerControls.childCount) {
            val button = centerControls.getChildAt(index)
            button.updateLayoutParams<ViewGroup.LayoutParams> {
                if (width > 0) width = (width * buttonScale).roundToInt()
                if (height > 0) height = (height * buttonScale).roundToInt()
            }
            button.setPadding(
                (button.paddingLeft * buttonScale).roundToInt(),
                (button.paddingTop * buttonScale).roundToInt(),
                (button.paddingRight * buttonScale).roundToInt(),
                (button.paddingBottom * buttonScale).roundToInt(),
            )
        }
        binding.playbackControlsHost.addView(
            centerControls,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        listOf(binding.frameControlsHost, binding.playbackControlsHost).forEach { controls ->
            BlurView3Util(controls, 12, 12f.dp).setBlurWithOverlayNoNoise(
                binding.videoBlurTarget,
                0x44000000,
                0x88000000.toInt(),
            )
        }
        binding.root.onClick(0) {
            if (player == null) dismiss() else toggleControls()
        }
        binding.closePreviewBtn.onClick { dismiss() }
        binding.videoPlayer.findViewById<View>(androidx.media3.ui.R.id.exo_time).gone()
        playbackSettingsBtn.visibility = View.INVISIBLE
        binding.previewSettingsBtn.onClick { showSettings() }
        binding.videoPlayer.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
            if (player != null) {
                val controlsVisibility = if (visibility == View.VISIBLE) View.VISIBLE else View.INVISIBLE
                binding.closePreviewBtn.visibility = controlsVisibility
                binding.videoTimeTv.visibility = controlsVisibility
                binding.frameControlsHost.visibility = controlsVisibility
                binding.playbackControlsHost.visibility = controlsVisibility
                binding.previewSettingsBtn.visibility = controlsVisibility
                if (visibility == View.VISIBLE) updateVideoProgress()
            }
        })
        binding.stepFrameBtn.onClick(0) { stepFrames(1) }
        binding.nextTenFramesBtn.onClick(0) { stepFrames(10) }
        binding.backTenFramesBtn.onClick(0) { stepFrames(-10) }
        binding.mediaHost.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            // 时间文本刷新也会触发布局，只有区域尺寸变化时才恢复位置，避免覆盖正在拖动的坐标。
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                restoreControlsPositions()
            }
        }
        initControlsDrag(binding.frameControlsHost, "preview_frame_controls")
        initControlsDrag(binding.playbackControlsHost, "preview_playback_controls")
    }

    fun updateInsets(statusBarHeight: Int, navigationBarHeight: Int) {
        binding.mediaHost.updateLayoutParams<FrameLayout.LayoutParams> {
            topMargin = statusBarHeight + 16.dp
            bottomMargin = navigationBarHeight + 32.dp
        }
    }

    fun show(bean: ShareInBean, isVideo: Boolean) {
        dismiss()
        backCallback.isEnabled = true
        blurTarget.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        if (!isVideo) {
            val sourceImg = ImageView(activity).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = bean.name
            }
            val preview = FullScreenBitmapPreviewView(activity, onHidden = {
                imagePreview = null
                imageSource?.clearByGlide()
                imageSource = null
                backCallback.isEnabled = false
                blurTarget.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            })
            imageSource = sourceImg
            imagePreview = preview
            preview.show(sourceImg) { ready ->
                val (width, height) = activity.getScreenFullSize()
                // 按窗口尺寸解码，避免离屏绘制时生成全尺寸大图。
                Glide.with(sourceImg)
                    .load(bean.uri)
                    .override(width, height)
                    .fitCenter()
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .dontAnimate()
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<Drawable>,
                            isFirstResource: Boolean,
                        ): Boolean {
                            sourceImg.post {
                                if (imageSource === sourceImg) {
                                    if (e != null) {
                                        logEx(throwable = e) { "Load image preview failed" }
                                    } else {
                                        loge { "Load image preview failed" }
                                    }
                                    dismiss()
                                    ToastBuilder().setOnTopLater()
                                        .setMessage(Globals.app.getString(R.string.something_error)).toast()
                                }
                            }
                            return false
                        }

                        override fun onResourceReady(
                            resource: Drawable,
                            model: Any,
                            target: Target<Drawable>?,
                            dataSource: DataSource,
                            isFirstResource: Boolean,
                        ): Boolean {
                            ready()
                            return false
                        }
                    })
                    .into(sourceImg)
            }
            return
        }

        binding.root.visible()
        binding.closePreviewBtn.visible()
        binding.closePreviewBtn.requestFocus()
        binding.videoPlayer.visible()
        binding.videoTimeTv.visible()
        binding.frameControlsHost.visible()
        binding.playbackControlsHost.visible()
        binding.previewSettingsBtn.visible()
        binding.frameControlsHost.translationX = 0f
        binding.frameControlsHost.translationY = 0f
        binding.playbackControlsHost.translationX = 0f
        binding.playbackControlsHost.translationY = 0f
        binding.videoPlayer.controllerShowTimeoutMs = 2000
        zoomPercent = mmkvGetInt(zoomPreferenceKey, 100).let { if (it in zoomPercents) it else 100 }
        binding.videoPlayer.findViewById<View>(androidx.media3.ui.R.id.exo_content_frame).apply {
            scaleX = zoomPercent / 100f
            scaleY = zoomPercent / 100f
        }
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
        binding.frameControlsHost.doOnLayout { restoreControlsPositions() }
        binding.playbackControlsHost.doOnLayout { restoreControlsPositions() }
        progressJob = scope.launch {
            while (isActive) {
                if (binding.videoPlayer.isControllerFullyVisible) updateVideoProgress()
                val updateInterval = if (videoPlayer.isPlaying && binding.videoPlayer.isControllerFullyVisible) {
                    16.milliseconds
                } else {
                    100.milliseconds
                }
                delay(updateInterval)
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

    private fun toggleControls() {
        if (binding.videoPlayer.isControllerFullyVisible) {
            binding.videoPlayer.hideController()
        } else {
            binding.videoPlayer.showController()
        }
    }

    private fun showSettings() {
        if (player == null) return
        settingsPopup?.dismiss()
        binding.videoPlayer.controllerShowTimeoutMs = 0
        var openingPlaybackSettings = false
        val popup = PopupMenu(binding.root.context, binding.previewSettingsBtn)
        popup.menu.add(R.string.preview_playback_settings).setOnMenuItemClickListener {
            openingPlaybackSettings = true
            binding.videoPlayer.controllerShowTimeoutMs = 2000
            playbackSettingsBtn.performClick()
            true
        }
        val zoomMenu = popup.menu.addSubMenu(R.string.preview_center_zoom)
        zoomPercents.forEach { percent ->
            zoomMenu.add(0, percent, 0, "$percent%").apply {
                isCheckable = true
                isChecked = percent == zoomPercent
                setOnMenuItemClickListener {
                    zoomPercent = percent
                    mmkvSetAny(zoomPreferenceKey, percent)
                    binding.videoPlayer.findViewById<View>(androidx.media3.ui.R.id.exo_content_frame).apply {
                        scaleX = percent / 100f
                        scaleY = percent / 100f
                    }
                    true
                }
            }
        }
        zoomMenu.setGroupCheckable(0, true, true)
        popup.setOnDismissListener {
            settingsPopup = null
            if (!openingPlaybackSettings) binding.videoPlayer.controllerShowTimeoutMs = 2000
        }
        settingsPopup = popup
        popup.show()
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
        binding.videoPlayer.keepScreenOn = currentPlayer.isPlaying
        binding.videoTimeTv.text = Globals.app.getString(
            R.string.preview_video_progress,
            formatTime(currentPlayer.currentPosition, 2),
            if (hasDuration) formatTime(duration, 1) else "--:--:--.-",
        )
    }

    private fun formatTime(timeMs: Long, decimalPlaces: Int): String {
        val milliseconds = max(0L, timeMs)
        return String.format(
            Locale.getDefault(), "%02d:%02d:%02d.%0${decimalPlaces}d",
            milliseconds / 3_600_000,
            milliseconds / 60_000 % 60,
            milliseconds / 1000 % 60,
            milliseconds % 1000 / (if (decimalPlaces == 2) 10 else 100),
        )
    }

    private fun initControlsDrag(controls: PreviewControlsLayout, preferenceKey: String) {
        var initialX = 0f
        var initialY = 0f
        controls.onTouchStarted = {
            binding.videoPlayer.controllerShowTimeoutMs = 0
            initialX = controls.x
            initialY = controls.y
        }
        controls.onDrag = { dx, dy ->
            controls.x = initialX + dx
            controls.y = initialY + dy
            constrainControls(controls)
        }
        controls.onTouchFinished = { dragged ->
            constrainControls(controls)
            if (dragged) saveControlsPosition(controls, preferenceKey)
            binding.videoPlayer.controllerShowTimeoutMs = 2000
        }
    }

    private fun restoreControlsPositions() {
        if (player == null) return
        listOf(
            binding.frameControlsHost to "preview_frame_controls",
            binding.playbackControlsHost to "preview_playback_controls",
        ).forEach { (controls, key) ->
            if (controls.isTouchActive || controls.width == 0 || binding.mediaHost.width == 0) return@forEach
            val x = mmkvGetInt("${key}_x", -1)
            val y = mmkvGetInt("${key}_y", -1)
            if (x in 0..1000 && y in 0..1000) {
                controls.x = max(0, binding.mediaHost.width - controls.width) * x / 1000f
                controls.y = max(0, binding.mediaHost.height - controls.height) * y / 1000f
            }
            constrainControls(controls)
        }
    }

    private fun saveControlsPosition(controls: View, key: String) {
        val width = binding.mediaHost.width - controls.width
        val height = binding.mediaHost.height - controls.height
        mmkvSetAny("${key}_x", if (width > 0) (controls.x / width * 1000).roundToInt() else 0)
        mmkvSetAny("${key}_y", if (height > 0) (controls.y / height * 1000).roundToInt() else 0)
    }

    private fun constrainControls(controls: View) {
        val host = binding.mediaHost
        if (controls.width == 0 || host.width == 0 || host.height == 0) return
        // 在整个预览区域内拖动，避开顶部关闭按钮和底部播放进度条。
        val right = max(0f, host.width - controls.width.toFloat())
        val top = min(60f.dp, max(0f, host.height - controls.height.toFloat()))
        val bottom = max(top, host.height - 96f.dp - controls.height)
        controls.x = max(0f, min(controls.x, right))
        controls.y = max(top, min(controls.y, bottom))
    }

    fun dismiss() {
        imagePreview?.hide()
        imagePreview = null
        imageSource?.clearByGlide()
        imageSource = null
        backCallback.isEnabled = false
        settingsPopup?.dismiss()
        settingsPopup = null
        binding.videoPlayer.hideController()
        progressJob?.cancel()
        progressJob = null
        binding.videoPlayer.player = null
        player?.release()
        player = null
        binding.videoPlayer.keepScreenOn = false
        binding.videoPlayer.gone()
        binding.videoTimeTv.gone()
        binding.frameControlsHost.gone()
        binding.playbackControlsHost.gone()
        binding.previewSettingsBtn.gone()
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
