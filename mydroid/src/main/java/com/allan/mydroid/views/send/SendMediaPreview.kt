package com.allan.mydroid.views.send

import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.allan.mydroid.R
import com.allan.mydroid.beansinner.ShareInBean
import com.au.module_android.Globals
import com.au.module_android.glide.clearByGlide
import com.au.module_android.log.logEx
import com.au.module_android.log.loge
import com.au.module_android.utils.getScreenFullSize
import com.au.module_androidui.toast.ToastBuilder
import com.au.module_androidui.widget.AbsFullScreenPreviewView
import com.au.module_androidui.widget.FullScreenBitmapPreviewView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import eightbitlab.com.blurview.BlurTarget

/** 发送列表共用的媒体预览入口，协调图片与视频预览的返回键、生命周期和图片加载清理。 */
class SendMediaPreview(
    fragment: Fragment,
    private val blurTarget: BlurTarget,
) : DefaultLifecycleObserver {
    private val activity = fragment.requireActivity()
    private val scope = fragment.viewLifecycleOwner.lifecycleScope
    private var preview: AbsFullScreenPreviewView? = null
    private var imageSource: ImageView? = null
    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            dismiss()
        }
    }

    init {
        fragment.viewLifecycleOwner.lifecycle.addObserver(this)
        activity.onBackPressedDispatcher.addCallback(fragment.viewLifecycleOwner, backCallback)
    }

    fun show(bean: ShareInBean, isVideo: Boolean) {
        dismiss()
        backCallback.isEnabled = true
        blurTarget.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        if (isVideo) {
            val videoPreview = SendVideoPreviewView(activity).apply {
                scope = this@SendMediaPreview.scope
                onHidden = ::onPreviewHidden
            }
            preview = videoPreview
            videoPreview.show(activity, bean)
            return
        }

        val sourceImg = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = bean.name
        }
        val imagePreview = FullScreenBitmapPreviewView(activity).apply {
            onHidden = ::onPreviewHidden
        }
        imageSource = sourceImg
        preview = imagePreview
        imagePreview.show(activity, sourceImg) { ready ->
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
    }

    fun dismiss() {
        preview?.hide(activity)
        onPreviewHidden()
    }

    private fun onPreviewHidden() {
        preview = null
        imageSource?.clearByGlide()
        imageSource = null
        backCallback.isEnabled = false
        blurTarget.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
    }

    override fun onStop(owner: LifecycleOwner) {
        dismiss()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        dismiss()
    }
}
