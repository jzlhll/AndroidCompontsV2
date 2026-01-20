package com.au.module_androidui.widget

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import androidx.core.content.withStyledAttributes
import com.au.module_android.utils.ViewShadowBuilder
import com.au.module_androidui.R
import com.au.module_androidui.ui.BgBuildCustomFontTextIds
import com.au.module_androidui.ui.viewBackgroundBuild
import com.au.module_androidui.ui.viewShadowBuild

/**
 * @author au
 * Date: 2023/8/24
 */
open class BgBuildCustomFontText : CustomFontText {
    private var shadowBuilder: ViewShadowBuilder? = null

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        context.withStyledAttributes(attrs, R.styleable.BgBuildCustomFontText) {
            val bgBuilder = viewBackgroundBuild(this, BgBuildCustomFontTextIds)
            shadowBuilder = viewShadowBuild(this, BgBuildCustomFontTextIds, bgBuilder)
        }
    }

    override fun draw(canvas: Canvas) {
        shadowBuilder?.onDrawShadow(canvas, width.toFloat(), height.toFloat())
        super.draw(canvas)
    }
}
