package com.allan.mydroid.client.receive

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.allan.mydroid.views.getIcon
import com.au.module_android.utilsmedia.formatBytes
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.allan.mydroid.R
import com.allan.mydroid.client.ConnectToHostHeader
import com.allan.mydroid.client.ConnectionStatus
import com.allan.mydroid.client.TransferMessageDialog
import com.allan.mydroid.client.HostEndpoint
import com.allan.mydroid.client.api.WsConnectionState
import com.allan.mydroid.client.download.DownloadState
import com.allan.mydroid.client.download.DownloadTask
import com.au.module_androiduiex.preview.AppPreview
import com.au.module_androiduiex.styles.ComposeTypography
import com.au.module_androiduiex.styles.ComposeHeaderLeftTitle
import com.au.module_androidcolor.R as AndroidColorR

/** 对方文件与本地记录独立展示，远端移除文件或断连后仍可使用已下载文件。 */
@Composable
fun ConnectToHostReceiveScreen(
    viewModel: ConnectToHostReceiveViewModel,
    endpoint: HostEndpoint?,
    titleText: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val connected = state.connectionState == WsConnectionState.Connected
    var deleteTask by remember { mutableStateOf<DownloadTask?>(null) }
    Column(modifier.fillMaxSize().navigationBarsPadding()) {
        if (endpoint == null) {
            ComposeHeaderLeftTitle(titleText, AndroidColorR.drawable.icon_back, onBack,
                leftIconContentDescription = stringResource(R.string.transfer_back))
        } else {
            ConnectToHostHeader(titleText, endpoint.ip, endpoint.httpPort)
            ConnectionStatus(state.connectionState, viewModel::reconnect)
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (endpoint != null) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            BasicText(stringResource(R.string.transfer_remote_files), style = ComposeTypography.Font16M)
                            val totalSize = if (state.files.any { it.fileSize == null || it.fileSize < 0 }) {
                                stringResource(R.string.unknown_size)
                            } else {
                                formatBytes(state.files.sumOf { it.fileSize ?: 0L })
                            }
                            BasicText(stringResource(R.string.transfer_summary, state.files.size, totalSize),
                                style = ComposeTypography.Font14Desc91)
                        }
                        if (state.refreshing) CircularProgressIndicator(Modifier.size(24.dp))
                        else TextButton(onClick = viewModel::refreshList, enabled = connected) {
                            Text(stringResource(R.string.cd_rescan), style = ComposeTypography.Font14sp)
                        }
                    }
                }
                if (state.files.isEmpty() && !state.refreshing) item {
                    BasicText(stringResource(R.string.no_received_files), style = ComposeTypography.Font14Desc91)
                }
                items(state.files, key = { "remote-${it.uriUuid}" }) { bean ->
                    val existing = state.downloadTasks.find { it.uriUuid == bean.uriUuid }
                    Row {
                        Image(painterResource(getIcon(bean.name)), null, Modifier.padding(end = 8.dp).size(36.dp))
                        Column(Modifier.weight(1f)) {
                            BasicText(bean.name ?: bean.uriUuid, style = ComposeTypography.Font16M)
                            BasicText(bean.fileSizeStr, style = ComposeTypography.Font14Desc91)
                        }
                        Button(onClick = { viewModel.downloadFile(bean) }, enabled = connected &&
                            existing?.active != true && existing?.state != DownloadState.Completed) {
                            Text(stringResource(R.string.download), style = ComposeTypography.Font14sp)
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(top = 16.dp)) {
                    BasicText(stringResource(R.string.transfer_records), style = ComposeTypography.Font16M)
                    BasicText(stringResource(R.string.transfer_summary, state.downloadTasks.size,
                        if (state.downloadTasks.any { it.fileSize <= 0 && it.state != DownloadState.Completed }) {
                            stringResource(R.string.unknown_size)
                        } else {
                            formatBytes(state.downloadTasks.sumOf {
                                if (it.state == DownloadState.Completed) it.receivedBytes else it.fileSize
                            })
                        }),
                        style = ComposeTypography.Font14Desc91)
                    BasicText(stringResource(R.string.transfer_completed_count,
                        state.downloadTasks.count { it.state == DownloadState.Completed }, state.downloadTasks.size),
                        style = ComposeTypography.Font14Desc91)
                }
            }
            if (state.downloadTasks.isEmpty()) item {
                BasicText(stringResource(R.string.no_received_files), style = ComposeTypography.Font14Desc91)
            }
            items(state.downloadTasks, key = { it.attemptId }) { task ->
                DownloadRow(task, connected && state.files.any { it.uriUuid == task.uriUuid },
                    { viewModel.cancelDownload(task) }, { viewModel.retryDownload(task) },
                    { viewModel.openDownloadedFile(task) }, { viewModel.saveDownloadedFile(task) },
                    { viewModel.shareDownloadedFile(task) }, { deleteTask = task }, { viewModel.removeRecord(task) })
            }
        }
    }
    state.error?.let { TransferMessageDialog(it, viewModel::consumeError) }
    deleteTask?.let { task ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteTask = null },
            text = { Text(stringResource(R.string.transfer_delete_file, task.destFile.name), style = ComposeTypography.Font14sp) },
            confirmButton = { TextButton(onClick = { viewModel.deleteDownloadedFile(task); deleteTask = null }) {
                Text(stringResource(R.string.delete), style = ComposeTypography.Font14sp)
            } },
            dismissButton = { TextButton(onClick = { deleteTask = null }) {
                Text(stringResource(R.string.connect_to_host_cancel_upload), style = ComposeTypography.Font14sp)
            } }
        )
    }
}

@Composable
private fun DownloadRow(task: DownloadTask, canRetry: Boolean, onCancel: () -> Unit, onRetry: () -> Unit,
    onOpen: () -> Unit, onSave: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit, onRemove: () -> Unit) {
    Column {
        Row {
            Image(painterResource(getIcon(task.name)), null, Modifier.padding(end = 8.dp).size(36.dp))
            BasicText(if (task.state == DownloadState.Completed) task.destFile.name else task.name,
                style = ComposeTypography.Font16M, modifier = Modifier.weight(1f))
        }
        BasicText(task.fileSizeStr, style = ComposeTypography.Font14Desc91)
        BasicText(task.hostKey, style = ComposeTypography.Font14Desc91)
        val label = when (task.state) {
            DownloadState.Pending -> R.string.connect_to_host_pending
            DownloadState.Running -> R.string.download
            DownloadState.Canceling -> R.string.transfer_stopping
            DownloadState.Completed -> R.string.connect_to_host_upload_done
            DownloadState.Failed -> R.string.transfer_failed
            DownloadState.Canceled -> R.string.transfer_canceled
            DownloadState.Missing -> R.string.file_not_exist
        }
        BasicText(stringResource(label), style = ComposeTypography.Font14Desc91)
        if (task.active) {
            if (task.state == DownloadState.Running) {
                BasicText(stringResource(R.string.transfer_average_speed, formatBytes(task.bytesPerSecond)),
                    style = ComposeTypography.Font14Desc91)
            }
            LinearProgressIndicator(progress = { task.progress }, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = onCancel, enabled = task.state != DownloadState.Canceling) {
                Text(stringResource(R.string.connect_to_host_cancel_upload), style = ComposeTypography.Font14sp)
            }
        } else {
            if (task.state == DownloadState.Completed) {
                Row {
                    TextButton(onClick = onOpen) { Text(stringResource(R.string.open), style = ComposeTypography.Font14sp) }
                    TextButton(onClick = onSave) { Text(stringResource(R.string.export), style = ComposeTypography.Font14sp) }
                    TextButton(onClick = onShare) { Text(stringResource(R.string.share), style = ComposeTypography.Font14sp) }
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), style = ComposeTypography.Font14sp) }
                }
            } else {
                TextButton(onClick = onRetry, enabled = canRetry) {
                    Text(stringResource(R.string.connect_to_host_retry), style = ComposeTypography.Font14sp)
                }
            }
            TextButton(onClick = onRemove) {
                Text(stringResource(R.string.transfer_remove_record), style = ComposeTypography.Font14sp)
            }
        }
        task.error?.let { BasicText(it, style = ComposeTypography.Font14sp) }
    }
}

@Preview
@Composable
private fun DownloadPreview() {
    AppPreview { DownloadRow(DownloadTask("192.168.1.2", 8080, "1", "photo.jpg", "4 MB", 4194304,
        "image/jpeg", "", state = DownloadState.Running, receivedBytes = 1048576,
        bytesPerSecond = 524288), true, {}, {}, {}, {}, {}, {}, {}) }
}
