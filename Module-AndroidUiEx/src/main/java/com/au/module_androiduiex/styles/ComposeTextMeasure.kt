package com.au.module_androiduiex.styles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp

/** 按当前字体与系统字体缩放测量指定行数的文本高度。 */
@Composable
fun rememberTextLinesHeight(
    textStyle: TextStyle,
    lineCount: Int,
): Dp {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(textMeasurer, textStyle, lineCount, density) {
        with(density) {
            textMeasurer.measure(
                text = AnnotatedString(("Ag\n").repeat(lineCount - 1) + "Ag"),
                style = textStyle,
            ).size.height.toDp()
        }
    }
}
