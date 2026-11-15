package com.allan.picnote.ui.theme

import androidx.activity.compose.LocalActivity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import com.au.module_androiduiex.styles.ComposeTheme
import com.au.module_androiduiex.styles.composeThemeColorScheme

data class StatusColors(val foreground: Color, val background: Color)

/** 复用公共基础配色，为 PicNote 的业务操作提供独立语义颜色。 */
data class ThemePalette(val scheme: ColorScheme, val createDocument: StatusColors)

val BlackPalette = ThemePalette(composeThemeColorScheme(ComposeTheme.Black), StatusColors(Color(0xFF9CC08A), Color(0xFF24301C)))
val WhitePalette = ThemePalette(composeThemeColorScheme(ComposeTheme.White), StatusColors(Color(0xFF1F7536), Color(0xFFEAF5ED)))
val DraculaPalette = ThemePalette(composeThemeColorScheme(ComposeTheme.Dracula), StatusColors(Color(0xFF83E5A1), Color(0xFF2B3C36)))
val SagePalette = ThemePalette(composeThemeColorScheme(ComposeTheme.Sage), StatusColors(Color(0xFF236B35), Color(0xFFE0EEE3)))

fun themePalette(theme: ComposeTheme): ThemePalette = when (theme) {
    ComposeTheme.Black -> BlackPalette
    ComposeTheme.White -> WhitePalette
    ComposeTheme.Dracula -> DraculaPalette
    ComposeTheme.Sage -> SagePalette
}

val LocalThemePalette = staticCompositionLocalOf { SagePalette }

@Composable
fun PicNoteTheme(theme: ComposeTheme = ComposeTheme.Sage, content: @Composable () -> Unit) {
    val palette = themePalette(theme)
    val activity = LocalActivity.current
    SideEffect {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !theme.dark
                isAppearanceLightNavigationBars = !theme.dark
            }
        }
    }
    CompositionLocalProvider(LocalThemePalette provides palette) {
        MaterialTheme(colorScheme = palette.scheme, typography = Typography(), content = content)
    }
}
