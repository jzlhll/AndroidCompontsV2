package com.au.module_androidui.ui.base

import android.os.Bundle
import androidx.activity.addCallback
import androidx.annotation.CallSuper
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment

/**
 * 为 FragmentShellActivity 承载的页面提供窗口布局和系统返回处理。
 *
 * 通过 [androidx.activity.OnBackPressedDispatcher] 统一处理旧式返回键和 Android 13 及以上的平台返回事件。
 * 参见 [Android 返回 API 说明](https://devgex.com/zh-CN/article/00047770)。
 */
@Deprecated("基础框架的一环，请使用BindingFragment或者ViewFragment")
open class AbsFragment : Fragment(), IFullWindow {
    /**
     *  如果想支持back，比如一些别的逻辑，比如堆栈自身内部返回处理。
     *  return true则表示期待返回直接退出。或者默认customBackAction = null，也是直接返回。
     */
    open val customBackAction:(()->Boolean)? = null

    /** 是否由当前 Fragment 处理系统返回。嵌入页可设为 false，由宿主页处理。 */
    open var customBackActionEnable = true

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (customBackActionEnable) {
            // 提前注册，让页面后续注册的专用回调优先。
            requireActivity().onBackPressedDispatcher.addCallback(this) {
                if (customBackAction?.invoke() != false) {
                    requireActivity().finishAfterTransition()
                }
            }
        }
    }

    /**
     * 通常和viewPager搭配
     */
    open fun getTitle(): CharSequence? {
        return activity?.title
    }

    /**
     * 设置title
     */
    open fun setTitle(text: CharSequence) {
        activity?.title = text
    }

    /**
     * 设置title
     */
    open fun setTitle(@StringRes text: Int) {
        activity?.setTitle(text)
    }

    open fun isAutoHideIme() = false

    /**
     * 向承载当前内容页的 Dialog 所在 FragmentManager 返回结果。
     */
    protected fun setDialogFragmentResult(requestKey: String, bundle: Bundle) {
        findDialog(this)?.parentFragmentManager?.setFragmentResult(requestKey, bundle)
    }

    override fun immersiveMode() : ImmersiveMode = ImmersiveMode.PaddingBars
}
