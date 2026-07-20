package com.allan.mydroid.client.receive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.allan.mydroid.R
import com.allan.mydroid.client.ConnectToHostHeader
import com.allan.mydroid.client.HostEndpoint
import com.allan.mydroid.client.beans.RemoteFileBean
import com.allan.mydroid.client.download.DownloadState
import com.allan.mydroid.client.download.DownloadTask
import com.au.module_androiduiex.styles.ComposeTypography
import com.au.module_androiduiex.styles.noBackClickable

/**
 * client 从 host 接收文件页面（对应 host MyDroidMode.Send）。
 */
@Composable
fun ConnectToHostReceiveScreen(
    viewModel: ConnectToHostReceiveViewModel,
    endpoint: HostEndpoint,
    titleText: String,
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Column(modifier = Modifier.fillMaxSize()) {
            ConnectToHostHeader(
                titleText = titleText,
                ip = endpoint.ip,
                httpPort = endpoint.httpPort,
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    text = stringResource(R.string.transfer_list_title),
                    style = ComposeTypography.Font14Secondary,
                )
                if (uiState.refreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    BasicText(
                        text = stringResource(R.string.cd_rescan),
                        style = ComposeTypography.Font14M.copy(color = Color(0xFF1E88E5)),
                        modifier = Modifier.noBackClickable { viewModel.refreshList() }.padding(8.dp),
                    )
                }
            }

            if (uiState.files.isEmpty() && !uiState.refreshing) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    BasicText(
                        text = stringResource(R.string.no_received_files),
                        style = ComposeTypography.Font14Secondary.copy(textAlign = TextAlign.Center),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.files, key = { it.uriUuid }) { bean ->
                        val task = uiState.downloadTasks.find { it.uriUuid == bean.uriUuid }
                        FileRow(
                            bean = bean,
                            task = task,
                            onDownload = { viewModel.downloadFile(bean) },
                            onCancel = { viewModel.cancelDownload(bean.uriUuid) },
                            onRetry = { viewModel.retryDownload(bean.uriUuid) },
                            onOpen = { task?.let { viewModel.openDownloadedFile(it) } },
                        )
                    }
                }
            }
        }
    }

    uiState.error?.let { err ->
        AlertDialog(
            onDismissRequest = { viewModel.consumeError() },
            title = { Text(text = stringResource(R.string.tips)) },
            text = { Text(text = err) },
            confirmButton = {
                TextButton(onClick = { viewModel.consumeError() }) {
                    Text(text = stringResource(R.string.action_confirm))
                }
            },
        )
    }
}

@Composable
private fun FileRow(
    bean: RemoteFileBean,
    task: DownloadTask?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onOpen: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                BasicText(
                    text = bean.name ?: "unknown",
                    style = ComposeTypography.Font16M,
                )
                if (bean.fileSizeStr.isNotEmpty()) {
                    BasicText(
                        text = bean.fileSizeStr,
                        style = ComposeTypography.Font14DescC0,
                    )
                }
            }
            when (task?.state) {
                DownloadState.Running -> {
                    Button(
                        onClick = onCancel,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                    ) { Text(text = stringResource(R.string.connect_to_host_cancel_upload)) }
                }
                DownloadState.Completed -> {
                    Button(onClick = onOpen) { Text(text = stringResource(R.string.open)) }
                }
                DownloadState.Failed -> {
                    Button(onClick = onRetry) { Text(text = stringResource(R.string.connect_to_host_retry)) }
                }
                DownloadState.Pending -> {
                    OutlinedButton(onClick = {}, enabled = false) {
                        Text(text = stringResource(R.string.connect_to_host_pending))
                    }
                }
                DownloadState.Canceled -> {
                    Button(onClick = onRetry) { Text(text = stringResource(R.string.connect_to_host_retry)) }
                }
                null -> {
                    Button(onClick = onDownload) { Text(text = stringResource(R.string.download)) }
                }
            }
        }

        if (task != null && task.state == DownloadState.Running) {
            LinearProgressIndicator(
                progress = { task.progress },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(3.dp),
            )
        }
        if (task != null && task.state == DownloadState.Failed && !task.error.isNullOrEmpty()) {
            BasicText(
                text = task.error,
                style = ComposeTypography.Font14sp.copy(color = Color(0xFFC62828)),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
