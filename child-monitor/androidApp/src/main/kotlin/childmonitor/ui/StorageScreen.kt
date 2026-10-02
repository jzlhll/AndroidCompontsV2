package childmonitor.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import childmonitor.model.StorageOverview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import childmonitor.android.R
import childmonitor.model.UserPreferencesPatch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun StorageScreen(viewModel: RecordViewModel, onManage: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val snapshot by viewModel.runtime.snapshotFlow.collectAsStateWithLifecycle()
    val prefs = snapshot.settings?.preferences
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.runtime.records.revisionFlow.collect { viewModel.loadStorage() }
        }
    }
    fun save(days: Int) {
        val revision = prefs?.preferencesRevision ?: return
        scope.launch { viewModel.runtime.updatePreferences(UserPreferencesPatch(retentionDays = days), revision, UUID.randomUUID().toString()) }
    }
    Column(modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.storage_title), style = ComposeTypography.headlineSmall)
        state.storage?.let { StorageSummary(it) }
        if (state.failed || snapshot.error != null) Text(stringResource(R.string.operation_failed), style = ComposeTypography.bodyMedium)
        TextButton({ viewModel.loadStorage() }, enabled = !state.busy) { Text(stringResource(R.string.refresh), style = ComposeTypography.labelLarge) }
        Text(stringResource(R.string.storage_retention), style = ComposeTypography.titleMedium)
        listOf(0, 7, 30, 90).forEach { days ->
            FilterChip(prefs?.retentionDays == days, { if (days == 0) save(0) else selected = days }, enabled = prefs != null,
                label = { Text(if (days == 0) stringResource(R.string.retention_forever) else stringResource(R.string.retention_days, days), style = ComposeTypography.labelLarge) })
        }
        Text(stringResource(R.string.retention_explain), style = ComposeTypography.bodyMedium)
        Button(onManage) { Text(stringResource(R.string.manage), style = ComposeTypography.labelLarge) }
        TextButton(onBack) { Text(stringResource(R.string.back), style = ComposeTypography.labelLarge) }
    }
    if (selected != null) AlertDialog(onDismissRequest = { selected = null }, text = { Text(stringResource(R.string.retention_confirm), style = ComposeTypography.bodyMedium) },
        confirmButton = { TextButton({ val days = selected; selected = null; if (days != null) save(days) }) { Text(stringResource(R.string.save), style = ComposeTypography.labelLarge) } },
        dismissButton = { TextButton({ selected = null }) { Text(stringResource(R.string.cancel), style = ComposeTypography.labelLarge) } })
}

@Composable
fun PurgeConfirmation(onCancel: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onCancel, text = { Text(stringResource(R.string.purge_confirm), style = ComposeTypography.bodyMedium) },
        confirmButton = { TextButton(onConfirm) { Text(stringResource(R.string.delete), style = ComposeTypography.labelLarge) } },
        dismissButton = { TextButton(onCancel) { Text(stringResource(R.string.cancel), style = ComposeTypography.labelLarge) } })
}

fun formatBytes(bytes: Long): String = if (bytes >= 1_073_741_824) "%.1f GB".format(bytes / 1_073_741_824.0) else "%.1f MB".format(bytes / 1_048_576.0)

@Composable
private fun StorageSummary(value: StorageOverview) {
    Text(stringResource(R.string.storage_overview, formatBytes(value.usedBytes), formatBytes(value.availableBytes), value.pendingCount), style = ComposeTypography.bodyLarge)
}

@Preview(showBackground = true)
@Composable
private fun StoragePreview() { AppPreview { StorageSummary(StorageOverview(512_000_000, 8_000_000_000, 1)) } }
