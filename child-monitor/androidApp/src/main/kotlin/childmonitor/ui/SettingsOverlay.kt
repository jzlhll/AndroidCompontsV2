package childmonitor.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import childmonitor.android.R
import childmonitor.model.MonitorSettings
import childmonitor.model.SettingsSnapshot
import childmonitor.model.UiSnapshot
import childmonitor.model.UserPreferences

/** 在主界面组合树上叠加设置，保留预览、生命周期及运行中的监控任务。 */
@Composable
fun SettingsOverlay(onClose: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val title = stringResource(R.string.settings_title)
    BackHandler(onBack = onClose)
    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .45f)).clickable(onClick = onClose))
        Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 32.dp)
            .semantics { paneTitle = title }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            shape = RoundedCornerShape(16.dp)) {
            content()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsOverlayPreview() {
    AppPreview {
        val snapshot = UiSnapshot(ready = true, settings = SettingsSnapshot(0, MonitorSettings(), UserPreferences()))
        SettingsOverlay({}) {
            SettingsPanel(snapshot, {}, {}, rememberSettingsAccess(snapshot), Modifier.fillMaxSize().padding(16.dp))
        }
    }
}
