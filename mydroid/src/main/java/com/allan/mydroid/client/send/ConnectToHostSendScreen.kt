package com.allan.mydroid.client.send

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.allan.mydroid.views.getIcon
import com.au.module_android.utilsmedia.formatBytes
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.allan.mydroid.client.beans.SelectedFile
import com.allan.mydroid.client.beans.UploadState
import com.au.module_androiduiex.preview.AppPreview
import com.au.module_androiduiex.styles.ComposeTypography

/** 发送列表逐项展示准备、上传、校验及结果，重试只处理未成功项。 */
@Composable
fun ConnectToHostSendScreen(viewModel: ConnectToHostSendViewModel, endpoint: HostEndpoint, titleText: String) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        viewModel.onFilesPicked(uris.map { SelectedFile(it, it.lastPathSegment ?: "", 0) })
    }
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        ConnectToHostHeader(titleText, endpoint.ip, endpoint.httpPort)
        ConnectionStatus(state.connectionState, viewModel::reconnect)
        SendContent(state, { picker.launch("*/*") }, viewModel::startUpload,
            viewModel::cancelUpload, { viewModel.removeSelectedFile(it.uri) })
    }
    state.error?.let { TransferMessageDialog(it, viewModel::consumeError) }
}

@Composable
private fun ColumnScope.SendContent(
    state: ConnectToHostSendUiState,
    onPick: () -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onRemove: (SelectedFile) -> Unit
) {
    state.leftSpace?.let {
        BasicText(stringResource(R.string.storage_remaining) + it,
            style = ComposeTypography.Font14Desc91, modifier = Modifier.padding(16.dp))
    }
    if (state.preparingSelection) {
        BasicText(stringResource(R.string.transfer_preparing), style = ComposeTypography.Font14Desc91,
            modifier = Modifier.padding(horizontal = 16.dp))
    }
    LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.selectedFiles.isEmpty()) item {
            BasicText(stringResource(R.string.select_files_hint), style = ComposeTypography.Font14Desc91)
        }
        items(state.selectedFiles, key = { it.id }) { file ->
            Column {
                Row {
                    Image(painterResource(getIcon(file.name)), null, Modifier.padding(end = 8.dp).size(36.dp))
                    Column(Modifier.weight(1f)) {
                        BasicText(file.savedName ?: file.name, style = ComposeTypography.Font16M)
                        BasicText(if (file.size > 0) formatBytes(file.size) else stringResource(R.string.unknown_size),
                            style = ComposeTypography.Font14Desc91)
                        val label = when (file.state) {
                            UploadState.Pending -> R.string.connect_to_host_pending
                            UploadState.Preparing -> R.string.transfer_preparing
                            UploadState.Uploading -> R.string.connect_to_host_uploading
                            UploadState.Merging -> R.string.transfer_merging
                            UploadState.Completed -> R.string.connect_to_host_upload_done
                            UploadState.Failed -> R.string.transfer_failed
                            UploadState.Canceled -> R.string.transfer_canceled
                        }
                        BasicText(stringResource(label), style = ComposeTypography.Font14Desc91)
                    }
                    TextButton(onClick = { onRemove(file) }, enabled = !state.busy) {
                        Text(stringResource(R.string.transfer_remove_record), style = ComposeTypography.Font14sp)
                    }
                }
                if (file.state == UploadState.Uploading) {
                    BasicText(stringResource(R.string.transfer_average_speed, formatBytes(file.bytesPerSecond)),
                        style = ComposeTypography.Font14Desc91)
                }
                if (file.state == UploadState.Uploading || file.state == UploadState.Merging) {
                    LinearProgressIndicator(progress = { file.progress }, modifier = Modifier.fillMaxWidth())
                }
                file.error?.let { BasicText(it, style = ComposeTypography.Font14sp) }
            }
        }
    }
    val totalSize = if (state.selectedFiles.any { it.size <= 0 }) {
        stringResource(R.string.unknown_size)
    } else {
        formatBytes(state.selectedFiles.sumOf { it.size })
    }
    val completed = state.selectedFiles.count { it.state == UploadState.Completed }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        BasicText(stringResource(R.string.transfer_summary, state.selectedFiles.size, totalSize),
            style = ComposeTypography.Font14sp)
        BasicText(stringResource(R.string.transfer_completed_count, completed, state.selectedFiles.size),
            style = ComposeTypography.Font14Desc91)
    }
    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onPick, enabled = !state.busy, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.select_files_to_send), style = ComposeTypography.Font14sp)
        }
        if (state.uploading) {
            Button(onClick = onCancel, enabled = !state.stopping, modifier = Modifier.weight(1f)) {
                Text(stringResource(if (state.stopping) R.string.transfer_stopping else R.string.connect_to_host_cancel_upload),
                    style = ComposeTypography.Font14sp)
            }
        } else {
            Button(onClick = onStart, modifier = Modifier.weight(1f),
                enabled = !state.busy && state.connectionState == WsConnectionState.Connected &&
                    state.selectedFiles.any { it.state != UploadState.Completed }) {
                Text(stringResource(R.string.connect_to_host_start_upload), style = ComposeTypography.Font14sp)
            }
        }
    }
}

@Preview
@Composable
private fun SendPreview() {
    AppPreview {
        Column(Modifier.fillMaxSize()) {
            SendContent(ConnectToHostSendUiState(
                selectedFiles = listOf(
                    SelectedFile(android.net.Uri.EMPTY, "photo.jpg", 4194304,
                        state = UploadState.Completed, progress = 1f),
                    SelectedFile(android.net.Uri.EMPTY, "report.pdf", 8388608,
                        state = UploadState.Uploading, progress = 0.5f, bytesPerSecond = 2097152),
                    SelectedFile(android.net.Uri.EMPTY, "archive.zip", 16777216)
                ), uploading = true, connectionState = WsConnectionState.Connected
            ), {}, {}, {}, {})
        }
    }
}
