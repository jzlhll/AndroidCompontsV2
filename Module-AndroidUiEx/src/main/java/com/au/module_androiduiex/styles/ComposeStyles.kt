package com.au.module_androiduiex.styles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Compose 通用容器样式。
 *
 * @property backgroundColor 背景色。
 * @property strokeColor 描边色。
 * @property strokeWidth 描边宽度。
 * @property cornerRadius 圆角半径。
 */
data class ComposeContainerStyle(
    val backgroundColor: Color,
    val strokeColor: Color,
    val strokeWidth: Dp,
    val cornerRadius: Dp,
)

/** Compose 通用样式集合。 */
object ComposeStyles {
    /** 普通白色圆角面板样式。 */
    val WhiteRoundedPanel: ComposeContainerStyle
        @Composable
        @ReadOnlyComposable
        get() = ComposeContainerStyle(
            backgroundColor = ComposeColors.BackgroundBg,
            strokeColor = Color.White,
            strokeWidth = 1.dp,
            cornerRadius = 16.dp,
        )
}
