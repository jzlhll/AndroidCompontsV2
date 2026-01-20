package com.au.module_androidui.widget

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import androidx.core.content.withStyledAttributes
import com.au.module_android.utils.ViewShadowBuilder
import com.au.module_androidui.R
import com.au.module_androidui.ui.CustomButtonIds
import com.au.module_androidui.ui.viewBackgroundBuild
import com.au.module_androidui.ui.viewShadowBuild

/**
 * @author au
 * @date :2023/11/7 15:37
 * @description:
 */
class CustomButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : CustomFontText(context, attrs) {
    private var shadowBuilder: ViewShadowBuilder? = null

    init {
        context.withStyledAttributes(attrs, R.styleable.CustomButton) {
            val bgBuilder = viewBackgroundBuild(this, CustomButtonIds)
            shadowBuilder = viewShadowBuild(this, CustomButtonIds, bgBuilder)
        }
        isClickable = true
    }

    override fun draw(canvas: Canvas) {
        shadowBuilder?.onDrawShadow(canvas, width.toFloat(), height.toFloat())
        super.draw(canvas)
    }
}
