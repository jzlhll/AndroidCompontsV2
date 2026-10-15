package com.au.module_androidui.fontutil

import android.graphics.Typeface
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import com.au.module_androidui.widget.FontMode

/** 局部文字沿用当前字体族，以统一字重匹配自定义字体和系统 fallback。 */
class CustomFontStyleSpan(private val mode: FontMode) : MetricAffectingSpan() {
    override fun updateDrawState(tp: TextPaint) {
        applyFontStyle(tp)
    }

    override fun updateMeasureState(textPaint: TextPaint) {
        applyFontStyle(textPaint)
    }

    private fun applyFontStyle(paint: TextPaint) {
        paint.typeface = Typeface.create(paint.typeface, mode.weight, paint.typeface?.isItalic == true)
        paint.isFakeBoldText = false
        paint.textSkewX = 0f
    }
}
