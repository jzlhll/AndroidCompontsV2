package com.au.module_androidui.widget

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import com.au.module_android.utils.myHideSystemUI
import com.au.module_android.utils.myShowSystemUI

/**
 * 全屏预览容器，统一管理 Activity 顶层挂载和系统栏的隐藏、恢复。
 *
 * 子类添加自己的内容 View，通过 attachToContent 挂载到 android.R.id.content，
 * 内容准备完成后调用 showPreview 显示容器，并按 hideSystemUi 决定是否调用 myHideSystemUI。
 * hide 会调用子类的 onHide 清理内容、移除顶层容器，并恢复本次预览隐藏的系统栏。
 * restoreSystemUi 为 false 时跳过恢复，关闭已挂载的容器后通知 onHidden。
 * Activity 仅通过挂载、显示和隐藏方法传入；返回键和页面生命周期由调用方管理。
 */
abstract class AbsFullScreenPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    var hideSystemUi = true
    var onHidden: (() -> Unit)? = null
    private var systemUiHidden = false

    init {
        visibility = INVISIBLE
    }

    protected fun attachToContent(activity: Activity): Boolean {
        if (parent == null) {
            val contentHost = activity.findViewById<ViewGroup>(android.R.id.content) ?: return false
            contentHost.addView(
                this,
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
            )
        }
        return true
    }

    protected fun showPreview(activity: Activity) {
        if (parent == null) return
        visibility = VISIBLE
        if (hideSystemUi) {
            activity.myHideSystemUI()
            systemUiHidden = true
        }
    }

    fun hide(activity: Activity, restoreSystemUi: Boolean = true) {
        val wasAttached = parent != null
        onHide()
        visibility = INVISIBLE
        (parent as? ViewGroup)?.removeView(this)
        if (restoreSystemUi && systemUiHidden) {
            activity.myShowSystemUI()
        }
        systemUiHidden = false
        if (wasAttached) {
            onHidden?.invoke()
        }
    }

    protected open fun onHide() = Unit
}
