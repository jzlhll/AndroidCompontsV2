package com.au.module_android.widget

import android.content.Context
import android.util.AttributeSet
import com.au.module_android.R
import com.au.module_android.utils.CustomButtonIds
import com.au.module_android.utils.viewBackgroundBuild
import androidx.core.content.withStyledAttributes

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