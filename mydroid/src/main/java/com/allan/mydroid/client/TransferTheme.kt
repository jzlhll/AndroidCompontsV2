package com.allan.mydroid.client

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.allan.mydroid.views.compose.MyDroidComposeColors
import com.au.module_androiduiex.styles.ComposeColors

/** 传输页面和弹窗统一使用项目的明暗色资源。 */
@Composable
fun TransferTheme(content: @Composable () -> Unit) {
    val background = ComposeColors.BackgroundBg
    val colors = if (background.luminance() < 0.5f) darkColorScheme() else lightColorScheme()
    MaterialTheme(
        colorScheme = colors.copy(
            primary = ComposeColors.BlueLink,
            onPrimary = Color.White,
            background = background,
            onBackground = ComposeColors.TextPrimary,
            surface = MyDroidComposeColors.NormalBlock,
            surfaceContainerHigh = MyDroidComposeColors.NormalBlock,
            onSurface = ComposeColors.TextPrimary,
            onSurfaceVariant = ComposeColors.TextDesc,
            outline = ComposeColors.TextDesc,
            error = ComposeColors.Red,
        ),
        content = content,
    )
}
