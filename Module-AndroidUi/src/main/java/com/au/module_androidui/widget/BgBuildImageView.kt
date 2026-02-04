package com.au.module_androidui.widget

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.withStyledAttributes
import com.au.module_android.utils.ViewShadowBuilder
import com.au.module_androidui.R
import com.au.module_androidui.ui.BgBuildImageViewIds
import com.au.module_androidui.ui.viewBackgroundBuild
import com.au.module_androidui.ui.viewShadowBuild

/**
 * @author allan
 * @date :2024/3/12 14:46
 * @description:
 */
class BgBuildImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {
    private var shadowBuilder: ViewShadowBuilder? = null

    init {
        context.withStyledAttributes(attrs, R.styleable.BgBuildImageView) {
            val bgBuilder = viewBackgroundBuild(this, BgBuildImageViewIds)
            shadowBuilder = viewShadowBuild(this, BgBuildImageViewIds, bgBuilder)
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
