package com.au.module_androiduiex.landscape

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** 按可用宽度分配操作侧栏与内容区；系统栏和键盘安全区由调用方处理。 */
@Composable
fun LandscapePaneLayout(
    showSidePane: Boolean,
    sidePane: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    sidePaneFraction: Float = 0.3f,
    content: @Composable () -> Unit,
) {
    require(sidePaneFraction > 0f && sidePaneFraction < 1f) { "sidePaneFraction must be between 0 and 1" }
    Row(modifier.fillMaxSize()) {
        if (showSidePane) {
            Surface(
                modifier = Modifier.fillMaxWidth(sidePaneFraction).fillMaxHeight(),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                content = sidePane,
            )
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        Box(Modifier.weight(1f).fillMaxHeight()) { content() }
    }
}
