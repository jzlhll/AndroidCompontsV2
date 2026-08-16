package com.au.module_androiduiex.styles

import android.text.StaticLayout
import android.widget.TextView
import kotlin.math.ceil

/** 按 TextView 当前字体和行距测量指定行数的文本高度，返回 px。 */
fun TextView.measuredTextLinesHeight(lineCount: Int): Int {
    val sampleText = ("Ag\n").repeat(lineCount - 1) + "Ag"
    val sampleWidth = ceil(paint.measureText("Ag")).toInt() + 1
    return StaticLayout.Builder.obtain(sampleText, 0, sampleText.length, paint, sampleWidth)
        .setIncludePad(includeFontPadding)
        .setLineSpacing(lineSpacingExtra, lineSpacingMultiplier)
        .build()
        .height
}
