package childmonitor.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import childmonitor.android.R
import childmonitor.model.*

@Composable
fun SettingsPanel(snapshot: UiSnapshot, onChange: (MonitorSettings) -> Unit, onMore: () -> Unit,
    onClose: () -> Unit, onReposition: () -> Unit, modifier: Modifier = Modifier) {
    val config = snapshot.settings?.monitorSettings
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(stringResource(R.string.settings_title), style = ComposeTypography.titleLarge)
        if (config != null) {
            val enabled = snapshot.runState !in listOf(RunState.Starting, RunState.Stopping)
            SettingSwitch(R.string.detection_head_down, config.headDownEnabled, enabled) { onChange(config.copy(headDownEnabled = it)) }
            SettingSwitch(R.string.detection_head_tilt, config.headTiltEnabled, enabled) { onChange(config.copy(headTiltEnabled = it)) }
            SettingSwitch(R.string.detection_body_lean, config.bodyLeanEnabled, enabled) { onChange(config.copy(bodyLeanEnabled = it)) }
            SettingSwitch(R.string.detection_away, config.awayEnabled, enabled) { onChange(config.copy(awayEnabled = it)) }
            TimeChoice(R.string.head_down_time, config.headDownConfirmMs, listOf(3_000, 5_000, 10_000), enabled) { onChange(config.copy(headDownConfirmMs = it)) }
            TimeChoice(R.string.head_tilt_time, config.headTiltConfirmMs, listOf(3_000, 5_000, 10_000), enabled) { onChange(config.copy(headTiltConfirmMs = it)) }
            TimeChoice(R.string.body_lean_time, config.bodyLeanConfirmMs, listOf(3_000, 5_000, 10_000), enabled) { onChange(config.copy(bodyLeanConfirmMs = it)) }
            TimeChoice(R.string.away_confirm_time, config.awayConfirmMs, listOf(2_000, 3_000, 5_000), enabled) { onChange(config.copy(awayConfirmMs = it)) }
            TimeChoice(R.string.away_max_time, config.maxAwayMs, listOf(60_000, 120_000, 180_000), enabled) { onChange(config.copy(maxAwayMs = it)) }
            Text(stringResource(R.string.sensitivity), style = ComposeTypography.titleSmall)
            Row { Sensitivity.entries.forEach { value -> TextButton({ onChange(config.copy(sensitivity = value)) }, enabled = enabled) {
                Text(stringResource(when (value) { Sensitivity.Low -> R.string.low; Sensitivity.Standard -> R.string.standard; Sensitivity.High -> R.string.high }),
                    color = if (value == config.sensitivity) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    style = ComposeTypography.labelLarge)
            } } }
            SettingSwitch(R.string.sound_enabled, config.soundEnabled, enabled) { onChange(config.copy(soundEnabled = it)) }
            TimeChoice(R.string.reminder_repeat_time, config.repeatReminderMs, listOf(30_000, 60_000, 120_000), enabled) { onChange(config.copy(repeatReminderMs = it)) }
            TimeChoice(R.string.reminder_gap_time, config.reminderGapMs, listOf(10_000, 20_000, 30_000), enabled) { onChange(config.copy(reminderGapMs = it)) }
            if (snapshot.runState in listOf(RunState.Preparing, RunState.Monitoring)) TextButton(onReposition) {
                Text(stringResource(R.string.reposition), style = ComposeTypography.labelLarge)
            }
        }
        TextButton(onMore) { Text(stringResource(R.string.more_settings), style = ComposeTypography.labelLarge) }
        TextButton(onClose) { Text(stringResource(R.string.close), style = ComposeTypography.labelLarge) }
    }
}

@Composable
fun SettingsScreen(viewModel: MonitorViewModel, onAvatar: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val snapshot by viewModel.snapshotFlow.collectAsStateWithLifecycle()
    val settings = snapshot.settings
    Column(modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.more_settings), style = ComposeTypography.headlineSmall)
        if (settings != null) {
            Image(painterResource(AvatarResources.drawable(settings.preferences.avatarId)), stringResource(R.string.avatar_title), Modifier.size(100.dp).clickable(onClick = onAvatar))
            TextButton(onAvatar) { Text(stringResource(R.string.avatar_edit), style = ComposeTypography.labelLarge) }
            TimeChoice(R.string.darken_time, settings.preferences.darkenAfterMs, listOf(30_000, 60_000, 120_000), true) { value ->
                viewModel.command { viewModel.runtime.updatePreferences(UserPreferencesPatch(darkenAfterMs = value), settings.preferencesRevision, it) }
            }
            Text(stringResource(R.string.darken_description), style = ComposeTypography.bodyMedium)
            SettingSwitch(R.string.rest_enabled, settings.monitorSettings.restRemindEnabled, true) { value ->
                viewModel.command { viewModel.runtime.updateSettings(settings.monitorSettings.copy(restRemindEnabled = value), settings.monitorRevision, it) }
            }
            TimeChoice(R.string.rest_time, settings.monitorSettings.restRemindMs, listOf(1_200_000, 1_800_000, 2_700_000), true) { value ->
                viewModel.command { viewModel.runtime.updateSettings(settings.monitorSettings.copy(restRemindMs = value), settings.monitorRevision, it) }
            }
        }
        if (snapshot.error != null) Text(stringResource(R.string.operation_failed), style = ComposeTypography.bodyMedium)
        TextButton(onBack) { Text(stringResource(R.string.back), style = ComposeTypography.labelLarge) }
    }
}

@Composable
fun AvatarScreen(viewModel: MonitorViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val snapshot by viewModel.snapshotFlow.collectAsStateWithLifecycle()
    val prefs = snapshot.settings?.preferences
    var draft by rememberSaveable { mutableStateOf(prefs?.avatarId ?: AvatarCatalog.defaultId) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(stringResource(R.string.avatar_title), style = ComposeTypography.headlineSmall)
        Image(painterResource(AvatarResources.drawable(draft)), null, Modifier.size(180.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AvatarCatalog.ids.forEach { id ->
                FilterChip(selected = id == draft, onClick = { draft = id }, enabled = !saving,
                    label = { Image(painterResource(AvatarResources.drawable(id)), stringResource(R.string.avatar_title), Modifier.size(60.dp)) })
            }
        }
        if (failed) Text(stringResource(R.string.operation_failed), style = ComposeTypography.bodyMedium)
        Button({
            val revision = prefs?.preferencesRevision ?: return@Button
            saving = true
            viewModel.command {
                val result = viewModel.runtime.updatePreferences(UserPreferencesPatch(avatarId = draft), revision, it)
                saving = false
                failed = result is CommandResult.Failure
                if (!failed) onBack()
            }
        }, enabled = !saving && prefs != null) { Text(stringResource(R.string.save), style = ComposeTypography.labelLarge) }
        TextButton(onBack, enabled = !saving) { Text(stringResource(R.string.back), style = ComposeTypography.labelLarge) }
    }
}

@Composable
private fun SettingSwitch(label: Int, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(label), style = ComposeTypography.bodyLarge)
        Switch(checked, onChange, enabled = enabled)
    }
}
@Composable
private fun TimeChoice(label: Int, value: Long, choices: List<Long>, enabled: Boolean, onChange: (Long) -> Unit) {
    Text(stringResource(label), style = ComposeTypography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        choices.forEach { duration -> FilterChip(duration == value, { onChange(duration) }, enabled = enabled,
            label = { Text(stringResource(if (duration >= 60_000) R.string.minutes_value else R.string.seconds_value,
                duration / if (duration >= 60_000) 60_000 else 1_000), style = ComposeTypography.labelMedium) }) }
    }
}
@Preview(showBackground = true)
@Composable
private fun SettingsPanelPreview() { AppPreview { SettingsPanel(UiSnapshot(settings = SettingsSnapshot(0, MonitorSettings(), UserPreferences())), {}, {}, {}, {}) } }
