package com.au.module_androidui.widget

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.widget.RelativeLayout
import androidx.core.content.withStyledAttributes
import com.au.module_android.utils.ViewShadowBuilder
import com.au.module_androidui.R
import com.au.module_androidui.ui.BgBuildRelativeLayoutIds
import com.au.module_androidui.ui.viewBackgroundBuild
import com.au.module_androidui.ui.viewShadowBuild

/**
 * @author allan
 * @date :2024/3/12 14:46
 * @description:
 */
class BgBuildRelativeLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : RelativeLayout(context, attrs) {
    private var shadowBuilder: ViewShadowBuilder? = null

    init {
        context.withStyledAttributes(attrs, R.styleable.BgBuildRelativeLayout) {
            val bgBuilder = viewBackgroundBuild(this, BgBuildRelativeLayoutIds)
            shadowBuilder = viewShadowBuild(this, BgBuildRelativeLayoutIds, bgBuilder)
        }
    }

    override fun draw(canvas: Canvas) {
        shadowBuilder?.onDrawShadow(canvas, width.toFloat(), height.toFloat())
        super.draw(canvas)
    }
}
