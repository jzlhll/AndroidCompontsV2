package com.au.agit.ui.theme

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import com.au.agit.model.AppTheme
import com.au.agit.model.dark
import com.au.agit.model.resolve
import com.au.agit.model.ChangeType
import com.au.module_androiduiex.styles.ComposeAppTheme
import com.au.module_androiduiex.styles.ComposeTheme
import com.au.module_androiduiex.styles.composeThemeColorScheme

data class StatusColors(val foreground: Color, val background: Color)

/** 复用公共主题颜色，保留 Git 变更状态的固定语义。 */
data class ThemePalette(val scheme: ColorScheme, val statuses: Map<ChangeType, StatusColors>)

private fun status(foreground: Long, background: Long) = StatusColors(Color(foreground), Color(background))

private fun palette(theme: ComposeTheme, added: StatusColors, modified: StatusColors, conflicted: StatusColors): ThemePalette {
    val scheme = composeThemeColorScheme(theme)
    return ThemePalette(scheme, mapOf(
        ChangeType.Added to added,
        ChangeType.Modified to modified,
        ChangeType.Deleted to StatusColors(scheme.error, scheme.errorContainer),
        ChangeType.Renamed to StatusColors(scheme.tertiary, scheme.tertiaryContainer),
        ChangeType.Conflicted to conflicted,
    ))
}

val BlackPalette = palette(ComposeTheme.Black, status(0xFF9CC08A, 0xFF24301C), status(0xFF9DBDE6, 0xFF202936), status(0xFFE8B06B, 0xFF3A2A18))
val WhitePalette = palette(ComposeTheme.White, status(0xFF1F7536, 0xFFEAF5ED), status(0xFF315FBD, 0xFFEDF3FF), status(0xFF875300, 0xFFFFF3E0))
val DraculaPalette = palette(ComposeTheme.Dracula, status(0xFF83E5A1, 0xFF2B3C36), status(0xFF8CC9F6, 0xFF2B3549), status(0xFFF1BD79, 0xFF44392E))
val SagePalette = palette(ComposeTheme.Sage, status(0xFF236B35, 0xFFE0EEE3), status(0xFF2F668B, 0xFFE7EFF3), status(0xFF855315, 0xFFF5EEDC))
val AlucardPalette = palette(ComposeTheme.Alucard, status(0xFF14710A, 0xFFE3EACF), status(0xFF036A96, 0xFFE5E2EF), status(0xFF846E15, 0xFFFFFBEB))

fun themePalette(theme: AppTheme, systemDark: Boolean): ThemePalette = when (theme.resolve(systemDark)) {
    AppTheme.FollowSystem -> error("FollowSystem must resolve to a fixed theme")
    AppTheme.Black -> BlackPalette
    AppTheme.White -> WhitePalette
    AppTheme.Dracula -> DraculaPalette
    AppTheme.Sage -> SagePalette
    AppTheme.Alucard -> AlucardPalette
}

private fun AppTheme.toComposeTheme(): ComposeTheme = when (this) {
    AppTheme.FollowSystem -> ComposeTheme.FollowSystem
    AppTheme.Black -> ComposeTheme.Black
    AppTheme.White -> ComposeTheme.White
    AppTheme.Dracula -> ComposeTheme.Dracula
    AppTheme.Sage -> ComposeTheme.Sage
    AppTheme.Alucard -> ComposeTheme.Alucard
}

val LocalThemePalette = staticCompositionLocalOf { WhitePalette }

@Composable
fun AGitTheme(theme: AppTheme = AppTheme.White, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val resolved = theme.resolve(systemDark)
    val palette = themePalette(theme, systemDark)
    val activity = LocalActivity.current
    SideEffect {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !resolved.dark
                isAppearanceLightNavigationBars = !resolved.dark
            }
        }
    }
    CompositionLocalProvider(LocalThemePalette provides palette) {
        ComposeAppTheme(resolved.toComposeTheme(), content)
    }
}
