package com.au.module_androidui.widget

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.withStyledAttributes
import com.au.module_android.utils.ViewShadowBuilder
import com.au.module_androidui.R
import com.au.module_androidui.ui.BgBuildConstraintLayoutIds
import com.au.module_androidui.ui.viewBackgroundBuild
import com.au.module_androidui.ui.viewShadowBuild

/**
 * @author allan
 * @date :2024/3/13 15:21
 * @description:
 */
open class BgBuildConstraintLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : ConstraintLayout(context, attrs) {
    private var shadowBuilder: ViewShadowBuilder? = null

    init {
        context.withStyledAttributes(attrs, R.styleable.BgBuildConstraintLayout) {
            val bgBuilder = viewBackgroundBuild(this, BgBuildConstraintLayoutIds)
            shadowBuilder = viewShadowBuild(this, BgBuildConstraintLayoutIds, bgBuilder)
        }
    }

    override fun draw(canvas: Canvas) {
        shadowBuilder?.onDrawShadow(canvas, width.toFloat(), height.toFloat())
        super.draw(canvas)
    }
}
