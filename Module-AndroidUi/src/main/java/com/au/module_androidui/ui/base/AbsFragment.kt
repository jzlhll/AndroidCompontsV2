package com.au.module_androidui.ui.base

import android.os.Build
import android.os.Bundle
import android.view.View
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.OnBackPressedCallback
import androidx.annotation.CallSuper
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment

/**
 * 这样所有的框架中的Fragment都实现了IFullWindow。但是由于只有在FragmentShellActivity中使用。故而没有问题。
 */
@Deprecated("基础框架的一环，请使用BindingFragment或者ViewFragment")
open class AbsFragment : Fragment(), IFullWindow {
    /**
     *  如果想支持back，比如一些别的逻辑，比如堆栈自身内部返回处理。
     *  return true则表示期待返回直接退出。或者默认customBackAction = null，也是直接返回。
     */
    open val customBackAction:(()->Boolean)? = null

    private val invokedBack:Any? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        OnBackInvokedCallback {
            customBack()
        }
    } else {
        null
    }

    /**
     * onViewCreated
     */
    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireActivity().onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, invokedBack as OnBackInvokedCallback)
        } else {
            requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object: OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    customBack()
                }
            })
        }
    }

    private fun customBack() {
        val action = customBackAction
        if (action == null) {
            requireActivity().finishAfterTransition()
        } else {
            if (action.invoke()) {
                requireActivity().finishAfterTransition()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireActivity().onBackInvokedDispatcher.unregisterOnBackInvokedCallback(invokedBack as OnBackInvokedCallback)
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

    override fun immersiveMode() : ImmersiveMode = ImmersiveMode.PaddingBars

    @Deprecated("这个禁用，因为我们自己的框架")
    final override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean) {
        super.onMultiWindowModeChanged(isInMultiWindowMode)
    }
}