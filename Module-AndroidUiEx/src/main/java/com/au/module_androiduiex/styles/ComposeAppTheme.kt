package com.au.module_androiduiex.styles

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class ComposeTheme(val dark: Boolean) { Black(true), White(false), Dracula(true), Sage(false), Alucard(false) }

private fun colorScheme(
    dark: Boolean, primary: Long, onPrimary: Long, background: Long, panel: Long,
    foreground: Long, muted: Long, border: Long, selected: Long,
    tertiary: Long, tertiaryContainer: Long, error: Long, errorContainer: Long,
): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = Color(primary), onPrimary = Color(onPrimary), primaryContainer = Color(selected), onPrimaryContainer = Color(foreground),
        secondary = Color(muted), onSecondary = Color(background), secondaryContainer = Color(selected), onSecondaryContainer = Color(foreground),
        tertiary = Color(tertiary), onTertiary = Color(background), tertiaryContainer = Color(tertiaryContainer), onTertiaryContainer = Color(tertiary),
        background = Color(background), onBackground = Color(foreground), surface = Color(background), onSurface = Color(foreground),
        surfaceVariant = Color(panel), onSurfaceVariant = Color(muted), surfaceTint = Color(primary),
        surfaceBright = Color(panel), surfaceDim = Color(background), surfaceContainerLowest = Color(background),
        surfaceContainerLow = Color(panel), surfaceContainer = Color(panel), surfaceContainerHigh = Color(panel), surfaceContainerHighest = Color(selected),
        outline = Color(muted), outlineVariant = Color(border),
        error = Color(error), onError = Color(background), errorContainer = Color(errorContainer), onErrorContainer = Color(error),
        inverseSurface = if (dark) Color(0xFFE6E4E0) else Color(0xFF282A2D),
        inverseOnSurface = if (dark) Color(0xFF282A2D) else Color(0xFFF6F6F3), inversePrimary = Color(primary),
    )
}

private val BlackScheme = colorScheme(true, 0xFFE0A458, 0xFF1C1204, 0xFF14110E, 0xFF201A14, 0xFFE8DCC8, 0xFFB0A48E, 0xFF332B21, 0xFF3A2C18,
    0xFFC89EC8, 0xFF302336, 0xFFE8A898, 0xFF3A221C)
private val WhiteScheme = colorScheme(false, 0xFF315FBD, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFF7F8FA, 0xFF27282E, 0xFF626675, 0xFFDFE1E5, 0xFFD4E2FF,
    0xFF7653A6, 0xFFF3EEFA, 0xFFAD2B38, 0xFFFFEEF0)
private val DraculaScheme = colorScheme(true, 0xFFBD93F9, 0xFF282A36, 0xFF282A36, 0xFF303241, 0xFFF8F8F2, 0xFFA1A9CD, 0xFF44475A, 0xFF444064,
    0xFFC8A7F5, 0xFF3B324E, 0xFFFF9AAE, 0xFF442D3C)
private val SageScheme = colorScheme(false, 0xFF326E40, 0xFFFFFFFF, 0xFFF8F9F7, 0xFFE3ECE4, 0xFF262B27, 0xFF5D6A62, 0xFFD0DED2, 0xFFD5E5D6,
    0xFF73539F, 0xFFEFEAF4, 0xFFA63A31, 0xFFF8E9E5)
private val AlucardScheme = colorScheme(false, 0xFFA3144D, 0xFFFFFFFF, 0xFFFFFBEB, 0xFFEFEDDC, 0xFF1F1F1F, 0xFF6C664B, 0xFFCECCC0, 0xFFECE9F2,
    0xFF644AC9, 0xFFE5E2EF, 0xFFB52F22, 0xFFF5DFD6)

fun composeThemeColorScheme(theme: ComposeTheme): ColorScheme = when (theme) {
    ComposeTheme.Black -> BlackScheme
    ComposeTheme.White -> WhiteScheme
    ComposeTheme.Dracula -> DraculaScheme
    ComposeTheme.Sage -> SageScheme
    ComposeTheme.Alucard -> AlucardScheme
}

/** 为独立 Compose 应用提供统一的主题颜色与标准 Material3 文字样式。 */
@Composable
fun ComposeAppTheme(theme: ComposeTheme, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = composeThemeColorScheme(theme), typography = Typography(), content = content)
}
