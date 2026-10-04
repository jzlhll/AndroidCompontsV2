package childmonitor.ui

import androidx.compose.foundation.Image
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
import childmonitor.android.BuildConfig
import childmonitor.android.R
import childmonitor.model.*

@Composable
fun SettingsPanel(snapshot: UiSnapshot, onChange: (MonitorSettings) -> Unit, onReposition: () -> Unit,
    access: SettingsAccess, modifier: Modifier = Modifier) {
    val config = snapshot.settings?.monitorSettings ?: return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingSwitch(R.string.detection_head_down, config.headDownEnabled, access) { onChange(config.copy(headDownEnabled = it)) }
        TimeChoice(R.string.head_down_time, config.headDownConfirmMs, listOf(3_000, 5_000, 10_000), access, available = config.headDownEnabled, featureLabel = R.string.detection_head_down) { onChange(config.copy(headDownConfirmMs = it)) }
        SettingSwitch(R.string.detection_head_tilt, config.headTiltEnabled, access) { onChange(config.copy(headTiltEnabled = it)) }
        TimeChoice(R.string.head_tilt_time, config.headTiltConfirmMs, listOf(3_000, 5_000, 10_000), access, available = config.headTiltEnabled, featureLabel = R.string.detection_head_tilt) { onChange(config.copy(headTiltConfirmMs = it)) }
        SettingSwitch(R.string.detection_body_lean, config.bodyLeanEnabled, access) { onChange(config.copy(bodyLeanEnabled = it)) }
        TimeChoice(R.string.body_lean_time, config.bodyLeanConfirmMs, listOf(3_000, 5_000, 10_000), access, available = config.bodyLeanEnabled, featureLabel = R.string.detection_body_lean) { onChange(config.copy(bodyLeanConfirmMs = it)) }
        SettingSwitch(R.string.detection_away, config.awayEnabled, access) { onChange(config.copy(awayEnabled = it)) }
        TimeChoice(R.string.away_confirm_time, config.awayConfirmMs, listOf(2_000, 3_000, 5_000), access, available = config.awayEnabled, featureLabel = R.string.detection_away) { onChange(config.copy(awayConfirmMs = it)) }
        TimeChoice(R.string.away_max_time, config.maxAwayMs, listOf(60_000, 120_000, 180_000), access, available = config.awayEnabled, featureLabel = R.string.detection_away) { onChange(config.copy(maxAwayMs = it)) }
        val sensitivityAvailable = config.headDownEnabled || config.headTiltEnabled || config.bodyLeanEnabled
        val sensitivityReason = stringResource(R.string.settings_enable_posture)
        RestrictedSetting(access, available = sensitivityAvailable, unavailableReason = sensitivityReason) { enabled ->
            Column {
                Text(stringResource(R.string.sensitivity), style = ComposeTypography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Sensitivity.entries.forEach { value ->
                        FilterChip(value == config.sensitivity, { access.run(sensitivityAvailable, unavailableReason = sensitivityReason) { onChange(config.copy(sensitivity = value)) } }, enabled = enabled,
                            label = { Text(stringResource(when (value) { Sensitivity.Low -> R.string.low; Sensitivity.Standard -> R.string.standard; Sensitivity.High -> R.string.high }), style = ComposeTypography.buttonLabelLarge) })
                    }
                }
                if (!sensitivityAvailable) Text(sensitivityReason, style = ComposeTypography.bodySmall)
            }
        }
        SettingSwitch(R.string.sound_enabled, config.soundEnabled, access) { onChange(config.copy(soundEnabled = it)) }
        TimeChoice(R.string.reminder_repeat_time, config.repeatReminderMs, listOf(30_000, 60_000, 120_000), access, available = config.soundEnabled, featureLabel = R.string.sound_enabled) { onChange(config.copy(repeatReminderMs = it)) }
        TimeChoice(R.string.reminder_gap_time, config.reminderGapMs, listOf(10_000, 20_000, 30_000), access, available = config.soundEnabled, featureLabel = R.string.sound_enabled) { onChange(config.copy(reminderGapMs = it)) }
        if (snapshot.runState in listOf(RunState.Preparing, RunState.Monitoring)) TextButton(onReposition) {
            Text(stringResource(R.string.reposition), style = ComposeTypography.buttonLabelLarge)
        }
    }
}

@Composable
fun SettingsScreen(viewModel: MonitorViewModel, onAvatar: () -> Unit, onBack: () -> Unit, onStorage: () -> Unit,
    onOpenProbe: () -> Unit, onReposition: () -> Unit, modifier: Modifier = Modifier) {
    val snapshot by viewModel.snapshotFlow.collectAsStateWithLifecycle()
    val settings = snapshot.settings
    val access = rememberSettingsAccess(snapshot)
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.settings_title), style = ComposeTypography.titleLarge)
            TextButton(onBack) { Text(stringResource(R.string.close), style = ComposeTypography.buttonLabelLarge) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!access.enabled) Text(stringResource(R.string.settings_unavailable), style = ComposeTypography.bodySmall)
            if (access.recording && access.enabled) Text(stringResource(R.string.settings_locked_hint), style = ComposeTypography.bodySmall)
            SettingsPanel(snapshot, onChange = { value ->
                settings?.let { config -> viewModel.command { viewModel.runtime.updateSettings(value, config.monitorRevision, it) } }
            }, onReposition = onReposition, access = access)
            if (settings != null) {
                SettingsAction(stringResource(R.string.avatar_edit), access, onAvatar)
                TimeChoice(R.string.darken_time, settings.preferences.darkenAfterMs, listOf(30_000, 60_000, 120_000), access, policy = SettingPolicy.Stopped) { value ->
                    viewModel.command { viewModel.runtime.updatePreferences(UserPreferencesPatch(darkenAfterMs = value), settings.preferencesRevision, it) }
                }
                Text(stringResource(R.string.darken_description), style = ComposeTypography.bodyMedium)
                SettingSwitch(R.string.rest_enabled, settings.monitorSettings.restRemindEnabled, access) { value ->
                    viewModel.command { viewModel.runtime.updateSettings(settings.monitorSettings.copy(restRemindEnabled = value), settings.monitorRevision, it) }
                }
                TimeChoice(R.string.rest_time, settings.monitorSettings.restRemindMs, listOf(1_200_000, 1_800_000, 2_700_000), access, available = settings.monitorSettings.restRemindEnabled, featureLabel = R.string.rest_enabled) { value ->
                    viewModel.command { viewModel.runtime.updateSettings(settings.monitorSettings.copy(restRemindMs = value), settings.monitorRevision, it) }
                }
            }
            SettingsEnhancements(viewModel, snapshot, access)
            SettingsAction(stringResource(R.string.storage_title), access, onStorage)
            if (BuildConfig.DEBUG) SettingsAction(stringResource(R.string.probe_title), access, onOpenProbe, policy = SettingPolicy.Stopped)
            ParentSettings(access = access)
            if (snapshot.error != null) Text(stringResource(R.string.operation_failed), style = ComposeTypography.bodyMedium)
        }
    }
}

@Composable
fun SettingsAction(label: String, access: SettingsAccess, onClick: () -> Unit, modifier: Modifier = Modifier,
    available: Boolean = true, policy: SettingPolicy = SettingPolicy.Live, unavailableReason: String? = null) {
    RestrictedSetting(access, modifier, available, policy, unavailableReason) { enabled ->
        TextButton({ access.run(available, policy, unavailableReason, onClick) }, enabled = enabled) {
            Text(settingLabel(label, access, policy), style = ComposeTypography.buttonLabelLarge)
        }
    }
}

@Composable
fun AvatarScreen(viewModel: MonitorViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val snapshot by viewModel.snapshotFlow.collectAsStateWithLifecycle()
    val access = rememberSettingsAccess(snapshot)
    val prefs = snapshot.settings?.preferences
    var draft by rememberSaveable { mutableStateOf(prefs?.avatarId ?: AvatarCatalog.defaultId) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(stringResource(R.string.avatar_title), style = ComposeTypography.headlineSmall)
        Image(painterResource(AvatarResources.drawable(draft)), null, Modifier.size(180.dp))
        RestrictedSetting(access, available = !saving) { enabled ->
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AvatarCatalog.ids.forEach { id ->
                    FilterChip(selected = id == draft, onClick = { access.run(!saving) { draft = id } }, enabled = enabled,
                        label = { Image(painterResource(AvatarResources.drawable(id)), stringResource(R.string.avatar_title), Modifier.size(60.dp)) })
                }
            }
        }
        if (failed) Text(stringResource(R.string.operation_failed), style = ComposeTypography.bodyMedium)
        SettingsAction(stringResource(R.string.save), access, {
            val revision = prefs?.preferencesRevision ?: return@SettingsAction
            saving = true
            viewModel.command {
                val result = viewModel.runtime.updatePreferences(UserPreferencesPatch(avatarId = draft), revision, it)
                saving = false
                failed = result is CommandResult.Failure
                if (!failed) onBack()
            }
        }, available = !saving && prefs != null)
        TextButton(onBack, enabled = !saving) { Text(stringResource(R.string.back), style = ComposeTypography.buttonLabelLarge) }
    }
}

@Composable
private fun SettingSwitch(label: Int, checked: Boolean, access: SettingsAccess, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    RestrictedSetting(access, modifier) { enabled ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(label), Modifier.weight(1f), style = ComposeTypography.bodyLarge)
            Switch(checked, { value -> access.run { onChange(value) } }, enabled = enabled)
        }
    }
}

@Composable
private fun TimeChoice(label: Int, value: Long, choices: List<Long>, access: SettingsAccess, modifier: Modifier = Modifier,
    available: Boolean = true, policy: SettingPolicy = SettingPolicy.Live, featureLabel: Int? = null, onChange: (Long) -> Unit) {
    val reason = featureLabel?.let { stringResource(R.string.settings_enable_feature, stringResource(it)) }
    RestrictedSetting(access, modifier, available, policy, reason) { enabled ->
        Column {
            Text(settingLabel(stringResource(label), access, policy), style = ComposeTypography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                choices.forEach { duration -> FilterChip(duration == value, { access.run(available, policy, reason) { onChange(duration) } }, enabled = enabled,
                    label = { Text(stringResource(if (duration >= 60_000) R.string.minutes_value else R.string.seconds_value,
                        duration / if (duration >= 60_000) 60_000 else 1_000), style = ComposeTypography.buttonLabelMedium) }) }
            }
            if (!available && reason != null) Text(reason, style = ComposeTypography.bodySmall)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsPanelPreview() {
    AppPreview {
        val snapshot = UiSnapshot(ready = true, settings = SettingsSnapshot(0, MonitorSettings(), UserPreferences()))
        SettingsPanel(snapshot, {}, {}, rememberSettingsAccess(snapshot), Modifier.padding(16.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun RecordingSettingsPreview() {
    AppPreview {
        val snapshot = UiSnapshot(ready = true, runState = RunState.Monitoring, captureReleased = false, settings = SettingsSnapshot(0, MonitorSettings(), UserPreferences()))
        SettingsPanel(snapshot, {}, {}, rememberSettingsAccess(snapshot), Modifier.padding(16.dp))
    }
}
