package childmonitor.ui

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
        Canvas(Modifier.fillMaxWidth().height(48.dp)) {
            val x = size.width / 2
            drawRect(Color.Gray, Offset(x - 24.dp.toPx(), 2.dp.toPx()), Size(48.dp.toPx(), 44.dp.toPx()), style = Stroke(2.dp.toPx()))
            if (snapshot.emptySeatReady) {
                drawCircle(Color(0xFF6E9E86), 7.dp.toPx(), Offset(x, 13.dp.toPx()))
                drawLine(Color(0xFF6E9E86), Offset(x - 16.dp.toPx(), 32.dp.toPx()), Offset(x + 16.dp.toPx(), 32.dp.toPx()), 4.dp.toPx())
            }
        }
        Text(stringResource(when (snapshot.placementIssue) {
            "region" -> R.string.placement_region
            "moved" -> R.string.placement_moved
            "multiple" -> R.string.placement_multiple
            "lighting" -> R.string.placement_lighting
            "occupied" -> R.string.placement_occupied
            "empty" -> R.string.placement_empty
            "shoulders" -> R.string.placement_shoulders
            "outside" -> R.string.placement_outside
            "head" -> R.string.placement_head
            "upright" -> R.string.placement_upright
            else -> R.string.placement_stable
        }), style = ComposeTypography.bodySmall)
        val progress = when { snapshot.placementProgress < 0 -> 0f; snapshot.placementProgress > 1 -> 1f; else -> snapshot.placementProgress }
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun SettingsEnhancements(viewModel: MonitorViewModel, snapshot: UiSnapshot, modifier: Modifier = Modifier) {
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.sound_volume, (volume * 100).toInt()), style = ComposeTypography.bodyMedium)
        Slider(volume, { volume = it }, onValueChangeFinished = {
            viewModel.command { viewModel.runtime.updateSettings(config.copy(soundVolume = volume), revision, it) }
        })
        TextButton({
            lowVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) <= 1
            viewModel.command { viewModel.runtime.previewReminder("reminder_head_down", it) }
        }) { Text(stringResource(R.string.sound_preview), style = ComposeTypography.labelLarge) }
        if (lowVolume) Text(stringResource(R.string.sound_system_low), style = ComposeTypography.bodySmall)
        OutlinedTextField(minutes, { minutes = it; invalid = false }, label = { Text(stringResource(R.string.target_duration), style = ComposeTypography.labelMedium) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, isError = invalid)
        if (invalid) Text(stringResource(R.string.target_invalid), style = ComposeTypography.bodySmall)
        Button({
            val value = minutes.toLongOrNull()
            invalid = value == null || value !in 0..480
            if (!invalid) viewModel.command { viewModel.runtime.updateSettings(config.copy(targetDurationMs = checkNotNull(value) * 60_000), revision, it) }
        }) { Text(stringResource(R.string.target_save), style = ComposeTypography.labelLarge) }
        Row {
            Checkbox(config.targetAutoStop, { value -> viewModel.command { viewModel.runtime.updateSettings(config.copy(targetAutoStop = value), revision, it) } })
            Text(stringResource(R.string.target_auto_stop), style = ComposeTypography.bodyMedium)
        }
        TextButton({ restore = true }) { Text(stringResource(R.string.settings_restore), style = ComposeTypography.labelLarge) }
        TextButton({ help = true }) { Text(stringResource(R.string.help_title), style = ComposeTypography.labelLarge) }
    }
    if (restore) AlertDialog(onDismissRequest = { restore = false }, text = { Text(stringResource(R.string.settings_restore_confirm), style = ComposeTypography.bodyMedium) },
        confirmButton = { TextButton({ restore = false; viewModel.command { viewModel.runtime.updateSettings(DefaultMonitorConfig.settings, revision, it) } }) { Text(stringResource(R.string.save), style = ComposeTypography.labelLarge) } },
        dismissButton = { TextButton({ restore = false }) { Text(stringResource(R.string.cancel), style = ComposeTypography.labelLarge) } })
    if (help) AlertDialog(onDismissRequest = { help = false }, title = { Text(stringResource(R.string.help_title), style = ComposeTypography.titleMedium) },
        text = { Column { Text(stringResource(R.string.help_body), style = ComposeTypography.bodyMedium); Text(stringResource(R.string.app_version, BuildConfig.VERSION_NAME), style = ComposeTypography.labelSmall) } },
        confirmButton = { TextButton({ help = false }) { Text(stringResource(R.string.close), style = ComposeTypography.labelLarge) } })
}

@Preview(showBackground = true)
@Composable
private fun PlacementGuidePreview() { AppPreview { PlacementGuide(UiSnapshot(placementIssue = "empty", placementProgress = .5f)) } }
