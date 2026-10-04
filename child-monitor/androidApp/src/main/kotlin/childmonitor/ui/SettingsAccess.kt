package childmonitor.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import childmonitor.android.R
import childmonitor.model.RunState
import childmonitor.model.UiSnapshot

/** 所有设置共用当前运行状态，受限控件仍可点击查看原因。 */
class SettingsAccess(private val context: Context, private val snapshot: () -> UiSnapshot) {
    val recording: Boolean get() = snapshot().runState !in listOf(RunState.Idle, RunState.Stopped) || !snapshot().captureReleased
    val enabled: Boolean get() = !recording && snapshot().ready && snapshot().settings != null && !snapshot().operationBusy

    fun run(available: Boolean = true, action: () -> Unit = {}) {
        if (enabled && available) action()
        else Toast.makeText(context, if (recording) R.string.settings_stop_recording else R.string.settings_unavailable, Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun rememberSettingsAccess(snapshot: UiSnapshot): SettingsAccess {
    val context = LocalContext.current
    val current = rememberUpdatedState(snapshot)
    return remember(context) { SettingsAccess(context) { current.value } }
}

@Composable
fun settingLabel(text: String, enabled: Boolean): String =
    if (enabled) text else stringResource(R.string.setting_unavailable_label, text)

@Composable
fun RestrictedSetting(access: SettingsAccess, modifier: Modifier = Modifier, available: Boolean = true, content: @Composable (Boolean) -> Unit) {
    val enabled = access.enabled && available
    Box(modifier) {
        content(enabled)
        if (!enabled) Box(Modifier.matchParentSize().clickable { access.run(available) })
    }
}
