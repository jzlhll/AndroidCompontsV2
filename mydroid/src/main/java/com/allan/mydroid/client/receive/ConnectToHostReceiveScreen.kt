package com.allan.mydroid.client.receive

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.allan.mydroid.R
import com.allan.mydroid.client.ConnectToHostHeader
import com.allan.mydroid.client.ConnectionStatus
import com.allan.mydroid.client.HostEndpoint
import com.allan.mydroid.client.TransferMessageDialog
import com.allan.mydroid.client.api.WsConnectionState
import com.allan.mydroid.client.beans.RemoteFileBean
import com.allan.mydroid.client.download.DownloadState
import com.allan.mydroid.client.download.DownloadTask
import com.allan.mydroid.views.compose.MyDroidComposeColors
import com.allan.mydroid.views.getIcon
import com.au.module_android.utilsmedia.formatBytes
import com.au.module_androiduiex.preview.AppPreview
import com.au.module_androiduiex.styles.ComposeColors
import com.au.module_androiduiex.styles.ComposeTypography

/** 展示对方文件及当前下载进度，已接收文件由本地接收列表统一管理。 */
@Composable
fun ConnectToHostReceiveScreen(
    viewModel: ConnectToHostReceiveViewModel,
    endpoint: HostEndpoint,
    titleText: String,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val connected = state.connectionState == WsConnectionState.Connected
    val displayedFiles = state.files + state.downloadTasks.filter { task ->
        task.active && state.files.none { it.uriUuid == task.uriUuid }
    }.map { task ->
        RemoteFileBean(name = task.name, uriUuid = task.uriUuid, fileSize = task.fileSize,
            fileSizeStr = if (task.fileSize > 0) formatBytes(task.fileSize) else stringResource(R.string.unknown_size))
    }
    Column(modifier.fillMaxSize().background(ComposeColors.BackgroundBg).navigationBarsPadding()) {
        ConnectToHostHeader(titleText, endpoint.ip, endpoint.httpPort)
        ConnectionStatus(state.connectionState, viewModel::reconnect)
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                        Text(stringResource(R.string.cd_rescan), style = ComposeTypography.Font14sp, color = LocalContentColor.current)
                    }
                }
            }
            if (displayedFiles.isEmpty() && !state.refreshing) item {
                BasicText(stringResource(R.string.no_received_files), style = ComposeTypography.Font14Desc91)
            }
            items(displayedFiles, key = { it.uriUuid }) { bean ->
                val task = state.downloadTasks.find { it.uriUuid == bean.uriUuid }
                RemoteFileRow(bean, connected, task,
                    onDownload = { viewModel.downloadFile(bean) },
                    onCancel = { task?.let(viewModel::cancelDownload) })
            }
        }
    }
    state.error?.let { TransferMessageDialog(it, viewModel::consumeError) }
}

@Composable
private fun RemoteFileRow(
    bean: RemoteFileBean,
    connected: Boolean,
    task: DownloadTask?,
    modifier: Modifier = Modifier,
    onDownload: () -> Unit = {},
    onCancel: () -> Unit = {}
) {
    Column(modifier.fillMaxWidth().background(MyDroidComposeColors.NormalBlock, RoundedCornerShape(12.dp))
        .padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(getIcon(bean.name)), null, Modifier.size(36.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicText(bean.name ?: bean.uriUuid, style = ComposeTypography.Font16M,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                BasicText(bean.fileSizeStr, style = ComposeTypography.Font14Desc91)
            }
            if (task?.active == true) {
                TextButton(onClick = onCancel, enabled = task.state != DownloadState.Canceling) {
                    Text(stringResource(R.string.connect_to_host_cancel_upload), style = ComposeTypography.Font14sp,
                        color = LocalContentColor.current)
                }
            } else {
                val label = when (task?.state) {
                    DownloadState.Completed -> R.string.connect_to_host_upload_done
                    DownloadState.Failed, DownloadState.Canceled -> R.string.connect_to_host_retry
                    else -> R.string.download
                }
                Button(onClick = onDownload, enabled = connected && task?.state != DownloadState.Completed) {
                    Text(stringResource(label), style = ComposeTypography.Font14sp, color = LocalContentColor.current)
                }
            }
        }
        if (task != null) {
            val label = when (task.state) {
                DownloadState.Pending -> R.string.connect_to_host_pending
                DownloadState.Running -> R.string.download
                DownloadState.Canceling -> R.string.transfer_stopping
                DownloadState.Completed -> R.string.connect_to_host_upload_done
                DownloadState.Failed -> R.string.transfer_failed
                DownloadState.Canceled -> R.string.transfer_canceled
            }
            if (task.state != DownloadState.Completed) {
                BasicText(stringResource(label), style = ComposeTypography.Font14Desc91)
            }
            if (task.active) {
                if (task.state == DownloadState.Running) {
                    BasicText(stringResource(R.string.transfer_average_speed, formatBytes(task.bytesPerSecond)),
                        style = ComposeTypography.Font14Desc91)
                }
                LinearProgressIndicator(progress = { task.progress }, modifier = Modifier.fillMaxWidth())
            }
            task.error?.let { Text(it, style = ComposeTypography.Font14sp, color = ComposeColors.Red) }
        }
    }
}

@Preview
@Composable
private fun DownloadPreview() {
    AppPreview {
        RemoteFileRow(
            RemoteFileBean(name = "photo.jpg", fileSizeStr = "4 MB", uriUuid = "1", fileSize = 4194304),
            connected = true,
            task = DownloadTask("192.168.1.2", 8080, "1", "photo.jpg", 4194304, "",
                state = DownloadState.Running, receivedBytes = 1048576, bytesPerSecond = 524288)
        )
    }
}
