package com.au.module_nested.fragments

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.CallSuper
import androidx.viewbinding.ViewBinding
import com.au.module_androidui.ui.bindings.BindingFragment
import com.au.module_androidui.ui.createViewBindingT2
import com.au.module_nested.databinding.NestedIndicatorLayoutBinding
import com.au.module_nested.smartrefresher.Colors

/**
 * @author allan
 * @date :2024/10/17 16:20
 * @description: 这里给出2个泛型，只是为了避免BindingFragment向上解析binding出错。
 * 所以第一个泛型就不要传入。第二个泛型就是你这个contentBinding的类。
 *
 * 只允许继承一次。否则需要修改createViewBindingT2的逻辑。
 */
abstract class AbsNestedIndicatorFragment<Void, ContentVB:ViewBinding> : BindingFragment<NestedIndicatorLayoutBinding>() {
    lateinit var contentBinding: ContentVB

    @CallSuper
    override fun onBindingCreated(savedInstanceState: Bundle?) {
        val vb = createViewBindingT2(javaClass, requireActivity().layoutInflater, binding.contentHost, isContentViewMergeXml()) as ContentVB
        contentBinding = vb
        if (!isContentViewMergeXml()) {
            binding.contentHost.addView(vb.root, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }

        if (isFakeRefresh()) {
            binding.root.refresher.initEarlyAsFake(binding.contentHost)
        } else {
            binding.root.refresher.initEarlyAsSmooth(binding.contentHost, binding.indicator, false)
        }
        binding.root.refresher.setIndicatorDeltaHoldY(requireActivity().resources.getDimension(com.au.module_nested.R.dimen.nested_indicator_toolbar_holdy))

        //子类实现，添加 refresher的setOnRefreshAction
        binding.root.refresher.setOnRefreshAction(onRefreshAction())

        binding.myToolbar.visibility = if (hasToolbar()) View.VISIBLE else View.GONE
    }

    fun setEnableRandomColor(enable:Boolean, pullColor:Int = Colors.sPullDownColor, refreshingColors:IntArray? = Colors.loadingColors()) {
        binding.root.refresher.setEnableRandomColor(enable, pullColor, refreshingColors)
    }

    abstract fun onRefreshAction(): () -> Unit

    abstract fun isContentViewMergeXml():Boolean

    /**
     * 是否想要一个假的刷新效果而已
     */
    abstract fun isFakeRefresh(): Boolean

    abstract fun hasToolbar(): Boolean
}