package com.au.module_androidui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.core.content.withStyledAttributes
import com.au.module_androidui.R
import com.au.module_androidui.ui.BgBuildViewIds
import com.au.module_androidui.ui.viewBackgroundBuild

/**
 * @author allan
 * @date :2024/3/12 14:46
 * @description:
 */
class BgBuildView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    init {
        context.withStyledAttributes(attrs, R.styleable.BgBuildView) {
            viewBackgroundBuild(this, BgBuildViewIds)
        }
    }
}