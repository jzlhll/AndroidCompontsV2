package childmonitor.ui

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import childmonitor.android.BuildConfig
import childmonitor.android.R
import childmonitor.model.*

/** 展示校准步骤和观测原因，进度仅表示当前连续有效样本。 */
@Composable
fun PlacementGuide(snapshot: UiSnapshot, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(stringResource(R.string.placement_steps), style = ComposeTypography.labelMedium)
        Text(stringResource(when (snapshot.placementIssue) {
            "person" -> R.string.placement_person
            "face_small" -> R.string.placement_face_small
            "moved" -> R.string.placement_moved
            "multiple" -> R.string.placement_multiple
            "lighting" -> R.string.placement_lighting
            "shoulders" -> R.string.placement_shoulders
            "head" -> R.string.placement_head
            "upright" -> R.string.placement_upright
            else -> R.string.placement_stable
        }), style = ComposeTypography.bodySmall)
        val progress = when { snapshot.placementProgress < 0 -> 0f; snapshot.placementProgress > 1 -> 1f; else -> snapshot.placementProgress }
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun SettingsEnhancements(viewModel: MonitorViewModel, snapshot: UiSnapshot, access: SettingsAccess, modifier: Modifier = Modifier) {
    val settings = snapshot.settings ?: return
    val config = settings.monitorSettings
    val revision = settings.monitorRevision
    val context = LocalContext.current
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var volume by remember(config.soundVolume) { mutableFloatStateOf(config.soundVolume) }
    var lowVolume by remember { mutableStateOf(false) }
    var minutes by rememberSaveable(config.targetDurationMs) { mutableStateOf((config.targetDurationMs / 60_000).toString()) }
    var invalid by remember { mutableStateOf(false) }
    var restore by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }
    val soundReason = stringResource(R.string.settings_enable_feature, stringResource(R.string.sound_enabled))
    val targetReason = stringResource(R.string.settings_target_required)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RestrictedSetting(access, available = config.soundEnabled, unavailableReason = soundReason) { enabled ->
            Column {
                Text(stringResource(R.string.sound_volume, (volume * 100).toInt()), style = ComposeTypography.bodyMedium)
                Slider(volume, { volume = it }, enabled = enabled, onValueChangeFinished = {
                    access.run(config.soundEnabled, unavailableReason = soundReason) { viewModel.command { viewModel.runtime.updateSettings(config.copy(soundVolume = volume), revision, it) } }
                })
                if (!config.soundEnabled) Text(soundReason, style = ComposeTypography.bodySmall)
                else if (access.recording) Text(stringResource(R.string.settings_volume_next_playback), style = ComposeTypography.bodySmall)
            }
        }
        SettingsAction(stringResource(R.string.sound_preview), access, {
            lowVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) <= 1
            viewModel.command { viewModel.runtime.previewReminder("reminder_head_down", it) }
        }, available = config.soundEnabled, unavailableReason = soundReason)
        if (lowVolume) Text(stringResource(R.string.sound_system_low), style = ComposeTypography.bodySmall)
        RestrictedSetting(access, policy = SettingPolicy.NextSession) { enabled ->
            OutlinedTextField(minutes, { minutes = it; invalid = false }, enabled = enabled,
                modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.target_duration), style = ComposeTypography.labelMedium) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, isError = invalid)
        }
        if (invalid) Text(stringResource(R.string.target_invalid), style = ComposeTypography.bodySmall)
        SettingsAction(stringResource(R.string.target_save), access, {
            val value = minutes.toLongOrNull()
            invalid = value == null || value !in 0..480
            if (!invalid) viewModel.command { viewModel.runtime.updateSettings(config.copy(targetDurationMs = checkNotNull(value) * 60_000), revision, it) }
        }, policy = SettingPolicy.NextSession)
        RestrictedSetting(access, available = config.targetDurationMs > 0, policy = SettingPolicy.NextSession, unavailableReason = targetReason) { enabled ->
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(config.targetAutoStop, { value -> access.run(config.targetDurationMs > 0, SettingPolicy.NextSession, targetReason) {
                    viewModel.command { viewModel.runtime.updateSettings(config.copy(targetAutoStop = value), revision, it) }
                } }, enabled = enabled)
                Text(stringResource(R.string.target_auto_stop), style = ComposeTypography.bodyMedium)
            }
        }
        if (config.targetDurationMs == 0L) Text(targetReason, style = ComposeTypography.bodySmall)
        if (access.recording) Text(stringResource(R.string.settings_target_next_session), style = ComposeTypography.bodySmall)
        SettingsAction(stringResource(R.string.settings_restore), access, { restore = true }, policy = SettingPolicy.Stopped)
        TextButton({ help = true }) { Text(stringResource(R.string.help_title), style = ComposeTypography.buttonLabelLarge) }
    }
    if (restore) AlertDialog(onDismissRequest = { restore = false }, text = { Text(stringResource(R.string.settings_restore_confirm), style = ComposeTypography.bodyMedium) },
        confirmButton = { TextButton({ restore = false; access.run(policy = SettingPolicy.Stopped) { viewModel.command { viewModel.runtime.restoreSettings(revision, it) } } }) { Text(stringResource(R.string.save), style = ComposeTypography.buttonLabelLarge) } },
        dismissButton = { TextButton({ restore = false }) { Text(stringResource(R.string.cancel), style = ComposeTypography.buttonLabelLarge) } })
    if (help) AlertDialog(onDismissRequest = { help = false }, title = { Text(stringResource(R.string.help_title), style = ComposeTypography.titleMedium) },
        text = { Column { Text(stringResource(R.string.help_body), style = ComposeTypography.bodyMedium); Text(stringResource(R.string.app_version, BuildConfig.VERSION_NAME), style = ComposeTypography.labelSmall) } },
        confirmButton = { TextButton({ help = false }) { Text(stringResource(R.string.close), style = ComposeTypography.buttonLabelLarge) } })
}

@Preview(showBackground = true)
@Composable
private fun PlacementGuidePreview() { AppPreview { PlacementGuide(UiSnapshot(placementIssue = "stable", placementProgress = .5f)) } }
