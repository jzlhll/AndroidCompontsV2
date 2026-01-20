package com.au.module_androidui.widget

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import androidx.core.content.withStyledAttributes
import com.au.module_android.utils.ViewShadowBuilder
import com.au.module_androidui.R
import com.au.module_androidui.ui.BgBuildViewIds
import com.au.module_androidui.ui.viewBackgroundBuild
import com.au.module_androidui.ui.viewShadowBuild

/**
 * @author allan
 * @date :2024/3/12 14:46
 * @description:
 */
class BgBuildView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    private var shadowBuilder: ViewShadowBuilder? = null

    init {
        context.withStyledAttributes(attrs, R.styleable.BgBuildView) {
            val bgBuilder = viewBackgroundBuild(this, BgBuildViewIds)
            shadowBuilder = viewShadowBuild(this, BgBuildViewIds, bgBuilder)
        }
    }

    override fun draw(canvas: Canvas) {
        // 在绘制背景/内容之前绘制阴影
        shadowBuilder?.onDrawShadow(canvas, width.toFloat(), height.toFloat())
        super.draw(canvas)
    }

    fun setShadowBuilder(builder: ViewShadowBuilder) {
        shadowBuilder = builder
        invalidate()
    }
}
