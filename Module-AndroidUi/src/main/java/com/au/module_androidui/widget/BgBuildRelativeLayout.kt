package com.au.module_androidui.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.RelativeLayout
import androidx.core.content.withStyledAttributes
import com.au.module_androidui.R
import com.au.module_androidui.ui.BgBuildRelativeLayoutIds
import com.au.module_androidui.ui.viewBackgroundBuild

/**
 * @author allan
 * @date :2024/3/12 14:46
 * @description:
 */
class BgBuildRelativeLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : RelativeLayout(context, attrs) {
    init {
        context.withStyledAttributes(attrs, R.styleable.BgBuildRelativeLayout) {
            viewBackgroundBuild(this, BgBuildRelativeLayoutIds)
        }
    }
}