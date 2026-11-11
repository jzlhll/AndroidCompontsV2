package com.allan.mydroid.views.send

import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.allan.mydroid.beansinner.ShareInBean
import com.au.module_androidui.widget.AbsFullScreenPreviewView
import eightbitlab.com.blurview.BlurTarget

/** 发送列表共用的媒体预览入口，协调图片与视频预览的返回键和生命周期。 */
class SendMediaPreview(
    fragment: Fragment,
    private val blurTarget: BlurTarget,
) : DefaultLifecycleObserver {
    private val activity = fragment.requireActivity()
    private val scope = fragment.viewLifecycleOwner.lifecycleScope
    private var preview: AbsFullScreenPreviewView? = null
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

        val imagePreview = SendImagePreviewView(activity).apply {
            onHidden = ::onPreviewHidden
        }
        preview = imagePreview
        imagePreview.show(activity, bean)
    }

    fun dismiss() {
        preview?.hide(activity)
        onPreviewHidden()
    }

    private fun onPreviewHidden() {
        preview = null
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
