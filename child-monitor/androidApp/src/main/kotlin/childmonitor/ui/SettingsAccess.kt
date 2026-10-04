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
import childmonitor.model.SettingPolicy
import childmonitor.model.UiSnapshot
import childmonitor.model.canChangeSettings

/** 按生效方式检查设置权限，只有必须停录的操作标记星号。 */
class SettingsAccess(private val context: Context, private val snapshot: () -> UiSnapshot) {
    val recording: Boolean get() = snapshot().runState !in listOf(RunState.Idle, RunState.Stopped) || !snapshot().captureReleased
    val enabled: Boolean get() = enabled(SettingPolicy.Live)

    fun enabled(policy: SettingPolicy): Boolean = snapshot().canChangeSettings(policy)
    fun requiresStop(policy: SettingPolicy): Boolean = recording && policy == SettingPolicy.Stopped

    fun run(available: Boolean = true, policy: SettingPolicy = SettingPolicy.Live,
        unavailableReason: String? = null, action: () -> Unit = {}) {
        if (enabled(policy) && available) action()
        else Toast.makeText(context, when {
            requiresStop(policy) -> context.getString(R.string.settings_stop_recording)
            !available && unavailableReason != null -> unavailableReason
            else -> context.getString(R.string.settings_unavailable)
        }, Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun rememberSettingsAccess(snapshot: UiSnapshot): SettingsAccess {
    val context = LocalContext.current
    val current = rememberUpdatedState(snapshot)
    return remember(context) { SettingsAccess(context) { current.value } }
}

@Composable
fun settingLabel(text: String, access: SettingsAccess, policy: SettingPolicy = SettingPolicy.Live): String =
    if (access.requiresStop(policy)) stringResource(R.string.setting_unavailable_label, text) else text

@Composable
fun RestrictedSetting(access: SettingsAccess, modifier: Modifier = Modifier, available: Boolean = true,
    policy: SettingPolicy = SettingPolicy.Live, unavailableReason: String? = null, content: @Composable (Boolean) -> Unit) {
    val enabled = access.enabled(policy) && available
    Box(modifier) {
        content(enabled)
        if (!enabled) Box(Modifier.matchParentSize().clickable { access.run(available, policy, unavailableReason) })
    }
}
