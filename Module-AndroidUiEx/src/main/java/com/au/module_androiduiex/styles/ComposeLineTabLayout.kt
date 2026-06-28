package com.au.module_androiduiex.styles

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.au.module_androidcolor.R

/** Compose 版本常规下划线 TabLayout 样式。 */
data class ComposeLineTabLayoutStyle(
    val height: Dp = 44.dp,
    val startPadding: Dp = 12.dp,
    val itemMinWidth: Dp = 80.dp,
    val itemHorizontalPadding: Dp = 20.dp,
    val bottomLineHeight: Dp = 0.5.dp,
    val indicatorHeight: Dp = 1.5.dp,
    val selectedTextStyle: TextStyle,
    val unselectedTextStyle: TextStyle,
)

/** 常规下划线 TabLayout 的默认样式。 */
object ComposeLineTabLayoutDefaults {
    val StyleNormally: ComposeLineTabLayoutStyle
        @Composable
        @ReadOnlyComposable
        get() = ComposeLineTabLayoutStyle(
            selectedTextStyle = ComposeTypography.Font16M,
            unselectedTextStyle = ComposeTypography.Font16,
        )
}

/** Compose 版本的常规下划线 TabLayout。 */
@Composable
fun ComposeLineTabLayout(
    tabs: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    style: ComposeLineTabLayoutStyle = ComposeLineTabLayoutDefaults.StyleNormally,
    selectedTextColor: Color = colorResource(R.color.color_tab_text_select),
    unselectedTextColor: Color = colorResource(R.color.color_tab_text_no_select),
    lineColor: Color = colorResource(R.color.color_line),
    indicatorColor: Color = colorResource(R.color.color_text_normal),
    onTabClick: (Int) -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(style.height),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(style.bottomLineHeight)
                .background(lineColor),
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState())
                .padding(start = style.startPadding),
        ) {
            tabs.forEachIndexed { index, title ->
                ComposeLineTabItem(
                    title = title,
                    selected = selectedIndex == index,
                    style = style,
                    selectedTextColor = selectedTextColor,
                    unselectedTextColor = unselectedTextColor,
                    indicatorColor = indicatorColor,
                ) {
                    onTabClick(index)
                }
            }
        }
    }
}

@Composable
private fun ComposeLineTabItem(
    title: String,
    selected: Boolean,
    style: ComposeLineTabLayoutStyle,
    selectedTextColor: Color,
    unselectedTextColor: Color,
    indicatorColor: Color,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    // later: onTextLayout 内更新 state 会多一次重组；tab 场景风险较低，可接受。
    var textWidth by remember(title) { mutableStateOf(0.dp) }

    Box(
        modifier = Modifier
            .height(style.height)
            .widthIn(min = style.itemMinWidth)
            .noBackClickable(onClick)
            .padding(horizontal = style.itemHorizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        val textStyle = if (selected) {
            style.selectedTextStyle.copy(color = selectedTextColor)
        } else {
            style.unselectedTextStyle.copy(color = unselectedTextColor)
        }
        BasicText(
            text = title,
            style = textStyle,
            onTextLayout = {
                textWidth = with(density) { it.size.width.toDp() }
            },
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .width(textWidth)
                    .height(style.indicatorHeight)
                    .background(indicatorColor, RoundedCornerShape(1.dp)),
            )
        }
    }
}
