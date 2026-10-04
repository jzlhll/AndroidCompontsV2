package childmonitor.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

val ComposeTypography = Typography()

val Typography.buttonBodyMedium: TextStyle get() = bodyMedium.enlargedButton()
val Typography.buttonLabelLarge: TextStyle get() = labelLarge.enlargedButton()
val Typography.buttonLabelMedium: TextStyle get() = labelMedium.enlargedButton()
val Typography.buttonTitleMedium: TextStyle get() = titleMedium.enlargedButton()

private fun TextStyle.enlargedButton() = copy(fontSize = (fontSize.value + 2).sp, lineHeight = (lineHeight.value + 2).sp)

@Composable
fun MonitorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF367A65),
            onPrimary = Color.White,
            background = Color(0xFFF5F7F2),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF253C34),
        ),
        typography = ComposeTypography,
        content = content,
    )
}

@Composable
fun AppPreview(content: @Composable () -> Unit) { MonitorTheme(content) }
