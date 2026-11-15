package com.au.module_androiduiex.styles

import androidx.compose.foundation.layout.*
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp

/*
 * agit 与 PicNote 共用的 Material3 按钮样式，统一最小高度与内容留白。
 * 默认颜色跟随当前 MaterialTheme，可配合 ComposeAppTheme 的颜色方案使用，
 * 也可放在其他 MaterialTheme 下；按钮本身不选择或切换颜色方案。
 * 圆角、交互反馈与禁用态继续使用 Material3 的默认行为。
 */

/** 实心按钮，用于主要操作；可通过 colors 覆盖当前主题提供的默认颜色。 */
@Composable
fun ComposeButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(), content: @Composable RowScope.() -> Unit) {
    androidx.compose.material3.Button(onClick, modifier.heightIn(min = ButtonDefaults.MinHeight + 6.dp), enabled = enabled,
        colors = colors, contentPadding = expandedComposeButtonPadding(ButtonDefaults.ContentPadding), content = content)
}

/** 描边按钮，用于需要弱化视觉权重的次要操作。 */
@Composable
fun ComposeOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    androidx.compose.material3.OutlinedButton(onClick, modifier.heightIn(min = ButtonDefaults.MinHeight + 6.dp), enabled = enabled,
        contentPadding = expandedComposeButtonPadding(ButtonDefaults.ContentPadding), content = content)
}

/** 文字按钮，用于轻量操作；默认增加上下留白，也允许调用方指定 contentPadding。 */
@Composable
fun ComposeTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    contentPadding: PaddingValues = expandedComposeButtonPadding(ButtonDefaults.TextButtonContentPadding), content: @Composable RowScope.() -> Unit) {
    androidx.compose.material3.TextButton(onClick, modifier.heightIn(min = ButtonDefaults.MinHeight + 6.dp), enabled = enabled,
        contentPadding = contentPadding, content = content)
}

/** 图标操作按钮，将最小高度设为 54dp；宽度沿用 Material3 或调用方的约束。 */
@Composable
fun ComposeIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    androidx.compose.material3.IconButton(onClick, modifier.heightIn(min = 54.dp), enabled = enabled, content = content)
}

/** 在原内边距的上下各增加 3dp，保留左右留白并遵循当前布局方向。 */
@Composable
fun expandedComposeButtonPadding(padding: PaddingValues): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(start = padding.calculateStartPadding(direction), top = padding.calculateTopPadding() + 3.dp,
        end = padding.calculateEndPadding(direction), bottom = padding.calculateBottomPadding() + 3.dp)
}
