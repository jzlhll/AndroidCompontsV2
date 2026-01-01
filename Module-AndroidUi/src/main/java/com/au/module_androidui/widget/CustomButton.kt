package com.au.module_androidui.widget

import android.content.Context
import android.util.AttributeSet
import androidx.core.content.withStyledAttributes
import com.au.module_androidui.R
import com.au.module_androidui.ui.CustomButtonIds
import com.au.module_androidui.ui.viewBackgroundBuild

/**
 * @author au
 * @date :2023/11/7 15:37
 * @description:
 */
class CustomButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : CustomFontText(context, attrs) {
    init {
        context.withStyledAttributes(attrs, R.styleable.CustomButton) {
            viewBackgroundBuild(this, CustomButtonIds)
        }
        isClickable = true
    }
}