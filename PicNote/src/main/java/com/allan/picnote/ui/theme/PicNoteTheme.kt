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
data class ThemePalette(
    val scheme: ColorScheme,
    val createDocument: StatusColors,
    val title: StatusColors,
    val introduction: StatusColors,
    val introductionContent: StatusColors,
    val chapter: StatusColors,
    val body: StatusColors,
) {
    fun nodeColors(kind: String): StatusColors = when (kind) {
        "title" -> title
        "introduction" -> introduction
        "chapter" -> chapter
        else -> body
    }
}

val BlackPalette = ThemePalette(composeThemeColorScheme(ComposeTheme.Black), StatusColors(Color(0xFF9CC08A), Color(0xFF24301C)),
    title = StatusColors(Color(0xFFD8B4FE), Color(0xFF261C32)), introduction = StatusColors(Color(0xFF93C5FD), Color(0xFF152538)),
    introductionContent = StatusColors(Color(0xFFA6A6A6), Color(0xFF242424)),
    chapter = StatusColors(Color(0xFFFCD34D), Color(0xFF302710)), body = StatusColors(Color(0xFF86EFAC), Color(0xFF142B20)))
val WhitePalette = ThemePalette(composeThemeColorScheme(ComposeTheme.White), StatusColors(Color(0xFF1F7536), Color(0xFFEAF5ED)),
    title = StatusColors(Color(0xFF6B21A8), Color(0xFFF3E8FF)), introduction = StatusColors(Color(0xFF1D4ED8), Color(0xFFEFF6FF)),
    introductionContent = StatusColors(Color(0xFF6B6B6B), Color(0xFFF0F0F0)),
    chapter = StatusColors(Color(0xFF92400E), Color(0xFFFFFBEB)), body = StatusColors(Color(0xFF166534), Color(0xFFF0FDF4)))
val DraculaPalette = ThemePalette(composeThemeColorScheme(ComposeTheme.Dracula), StatusColors(Color(0xFF83E5A1), Color(0xFF2B3C36)),
    title = StatusColors(Color(0xFFBD93F9), Color(0xFF382D4D)), introduction = StatusColors(Color(0xFF8BE9FD), Color(0xFF243C46)),
    introductionContent = StatusColors(Color(0xFFACABB6), Color(0xFF353640)),
    chapter = StatusColors(Color(0xFFFFB86C), Color(0xFF483626)), body = StatusColors(Color(0xFF50FA7B), Color(0xFF243E32)))
val SagePalette = ThemePalette(composeThemeColorScheme(ComposeTheme.Sage), StatusColors(Color(0xFF236B35), Color(0xFFE0EEE3)),
    title = StatusColors(Color(0xFF735084), Color(0xFFEDE4F0)), introduction = StatusColors(Color(0xFF365F82), Color(0xFFE1EBF2)),
    introductionContent = StatusColors(Color(0xFF666B65), Color(0xFFE9ECE6)),
    chapter = StatusColors(Color(0xFF865D24), Color(0xFFF2E9D7)), body = StatusColors(Color(0xFF365F41), Color(0xFFE1EBDF)))

val AlucardPalette = ThemePalette(composeThemeColorScheme(ComposeTheme.Alucard), StatusColors(Color(0xFF14710A), Color(0xFFE3EACF)),
    title = StatusColors(Color(0xFF644AC9), Color(0xFFE5E2EF)), introduction = StatusColors(Color(0xFF036A96), Color(0xFFEFEDDC)),
    introductionContent = StatusColors(Color(0xFF666660), Color(0xFFECEBE6)),
    chapter = StatusColors(Color(0xFF846E15), Color(0xFFFFFBEB)), body = StatusColors(Color(0xFF14710A), Color(0xFFE3EACF)))

fun themePalette(theme: ComposeTheme): ThemePalette = when (theme) {
    ComposeTheme.Black -> BlackPalette
    ComposeTheme.White -> WhitePalette
    ComposeTheme.Dracula -> DraculaPalette
    ComposeTheme.Sage -> SagePalette
    ComposeTheme.Alucard -> AlucardPalette
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
