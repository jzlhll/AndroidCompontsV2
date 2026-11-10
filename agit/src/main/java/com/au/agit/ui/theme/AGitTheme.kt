package com.au.agit.ui.theme

import androidx.activity.compose.LocalActivity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.core.view.WindowCompat
import com.au.agit.model.AppTheme
import com.au.agit.model.ChangeType

data class StatusColors(val foreground: Color, val background: Color)

/** 以 Notepadmm 的 Ember、Mist、Dracula、Sage 为基础，状态色保持固定语义。 */
data class ThemePalette(val scheme: ColorScheme, val statuses: Map<ChangeType, StatusColors>, val noGit: StatusColors)

private fun status(foreground: Long, background: Long) = StatusColors(Color(foreground), Color(background))

private fun grayColor(value: Long): Color {
    val source = Color(value)
    val gray = source.red * 0.2126f + source.green * 0.7152f + source.blue * 0.0722f
    return Color(gray, gray, gray, source.alpha)
}

private fun palette(
    dark: Boolean,
    primary: Long,
    onPrimary: Long,
    background: Long,
    panel: Long,
    foreground: Long,
    muted: Long,
    border: Long,
    selected: Long,
    statuses: Map<ChangeType, StatusColors>,
): ThemePalette {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val noGitBackground = grayColor(selected)
    val noGitForeground = grayColor(foreground).copy(alpha = 0.75f).compositeOver(noGitBackground)
    return ThemePalette(base.copy(
        primary = Color(primary), onPrimary = Color(onPrimary), primaryContainer = Color(selected), onPrimaryContainer = Color(foreground),
        secondary = Color(muted), onSecondary = Color(background), secondaryContainer = Color(selected), onSecondaryContainer = Color(foreground),
        tertiary = statuses.getValue(ChangeType.Renamed).foreground, onTertiary = Color(background),
        tertiaryContainer = statuses.getValue(ChangeType.Renamed).background, onTertiaryContainer = statuses.getValue(ChangeType.Renamed).foreground,
        background = Color(background), onBackground = Color(foreground), surface = Color(background), onSurface = Color(foreground),
        surfaceVariant = Color(panel), onSurfaceVariant = Color(muted), surfaceTint = Color(primary),
        surfaceBright = Color(panel), surfaceDim = Color(background), surfaceContainerLowest = Color(background),
        surfaceContainerLow = Color(panel), surfaceContainer = Color(panel), surfaceContainerHigh = Color(panel), surfaceContainerHighest = Color(selected),
        outline = Color(muted), outlineVariant = Color(border),
        error = statuses.getValue(ChangeType.Deleted).foreground, onError = Color(background),
        errorContainer = statuses.getValue(ChangeType.Deleted).background, onErrorContainer = statuses.getValue(ChangeType.Deleted).foreground,
        inverseSurface = if (dark) Color(0xFFE6E4E0) else Color(0xFF282A2D),
        inverseOnSurface = if (dark) Color(0xFF282A2D) else Color(0xFFF6F6F3), inversePrimary = Color(primary),
    ), statuses, StatusColors(noGitForeground, noGitBackground))
}

val BlackPalette = palette(true, 0xFFE0A458, 0xFF1C1204, 0xFF14110E, 0xFF201A14, 0xFFE8DCC8, 0xFFB0A48E, 0xFF332B21, 0xFF3A2C18, mapOf(
    ChangeType.Added to status(0xFF9CC08A, 0xFF24301C),
    ChangeType.Modified to status(0xFF9DBDE6, 0xFF202936),
    ChangeType.Deleted to status(0xFFE8A898, 0xFF3A221C),
    ChangeType.Renamed to status(0xFFC89EC8, 0xFF302336),
    ChangeType.Conflicted to status(0xFFE8B06B, 0xFF3A2A18),
))

val WhitePalette = palette(false, 0xFF315FBD, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFF7F8FA, 0xFF27282E, 0xFF626675, 0xFFDFE1E5, 0xFFD4E2FF, mapOf(
    ChangeType.Added to status(0xFF1F7536, 0xFFEAF5ED),
    ChangeType.Modified to status(0xFF315FBD, 0xFFEDF3FF),
    ChangeType.Deleted to status(0xFFAD2B38, 0xFFFFEEF0),
    ChangeType.Renamed to status(0xFF7653A6, 0xFFF3EEFA),
    ChangeType.Conflicted to status(0xFF875300, 0xFFFFF3E0),
))

val DraculaPalette = palette(true, 0xFFBD93F9, 0xFF282A36, 0xFF282A36, 0xFF303241, 0xFFF8F8F2, 0xFFA1A9CD, 0xFF44475A, 0xFF444064, mapOf(
    ChangeType.Added to status(0xFF83E5A1, 0xFF2B3C36),
    ChangeType.Modified to status(0xFF8CC9F6, 0xFF2B3549),
    ChangeType.Deleted to status(0xFFFF9AAE, 0xFF442D3C),
    ChangeType.Renamed to status(0xFFC8A7F5, 0xFF3B324E),
    ChangeType.Conflicted to status(0xFFF1BD79, 0xFF44392E),
))

val SagePalette = palette(false, 0xFF326E40, 0xFFFFFFFF, 0xFFF8F9F7, 0xFFE3ECE4, 0xFF262B27, 0xFF5D6A62, 0xFFD0DED2, 0xFFD5E5D6, mapOf(
    ChangeType.Added to status(0xFF236B35, 0xFFE0EEE3),
    ChangeType.Modified to status(0xFF2F668B, 0xFFE7EFF3),
    ChangeType.Deleted to status(0xFFA63A31, 0xFFF8E9E5),
    ChangeType.Renamed to status(0xFF73539F, 0xFFEFEAF4),
    ChangeType.Conflicted to status(0xFF855315, 0xFFF5EEDC),
))

fun themePalette(theme: AppTheme): ThemePalette = when (theme) {
    AppTheme.Black -> BlackPalette
    AppTheme.White -> WhitePalette
    AppTheme.Dracula -> DraculaPalette
    AppTheme.Sage -> SagePalette
}

val LocalThemePalette = staticCompositionLocalOf { WhitePalette }

@Composable
fun AGitTheme(theme: AppTheme = AppTheme.White, content: @Composable () -> Unit) {
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
