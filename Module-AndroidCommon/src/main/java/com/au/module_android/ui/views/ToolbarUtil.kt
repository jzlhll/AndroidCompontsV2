package com.au.module_android.ui.views

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.RelativeLayout
import androidx.viewbinding.ViewBinding
import com.au.module_android.databinding.ToolbarActivityLayoutBinding
import com.au.module_android.widget.YourToolbar

internal fun createToolbarLayout(context: Context, contentView:View) : ToolbarActivityLayoutBinding {
    val vb = ToolbarActivityLayoutBinding.inflate(LayoutInflater.from(context))
    val rl = vb.root
    val toolbar = vb.toolbar
    rl.addView(contentView, RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT).also {
        it.addRule(RelativeLayout.BELOW, toolbar.id)
    })
    return vb
}

/**
 * 使用如下方式来获取自定义的toolbarBinding
private val toolbarBinding by unsafeLazy {
toolbarYoursBinding<FragmentProfileToolbarBinding>()
}
 */
fun <VB : ViewBinding> IHasToolbar.toolbarYoursBinding() : VB? {
    val toolbarInfo = toolbarInfo() ?: return null
    if (toolbarInfo is YourToolbarInfo.Yours<*>) {
        val vb = toolbar?.getAsYourBinding(toolbarInfo.viewBindingInitializer)
        return vb as? VB
    }
    return null
}

fun <VB : ViewBinding> YourToolbar.toolbarYoursBinding(viewBindingInitializer: (View) -> VB) : VB? {
    return this.getAsYourBinding(viewBindingInitializer)
}