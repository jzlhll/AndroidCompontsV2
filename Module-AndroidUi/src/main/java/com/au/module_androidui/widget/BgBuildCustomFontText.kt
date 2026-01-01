package com.au.module_androidui.widget

import android.content.Context
import android.util.AttributeSet
import androidx.core.content.withStyledAttributes
import com.au.module_androidui.R
import com.au.module_androidui.ui.BgBuildCustomFontTextIds
import com.au.module_androidui.ui.viewBackgroundBuild

/**
 * @author au
 * Date: 2023/8/24
 */
open class BgBuildCustomFontText : CustomFontText {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        context.withStyledAttributes(attrs, R.styleable.BgBuildCustomFontText) {
            viewBackgroundBuild(this, BgBuildCustomFontTextIds)
        }
    }
}