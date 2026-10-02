package childmonitor.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val ComposeTypography = Typography()

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
