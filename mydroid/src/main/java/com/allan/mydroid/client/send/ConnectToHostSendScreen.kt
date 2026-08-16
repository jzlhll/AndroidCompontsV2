package com.allan.mydroid.client.send

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.allan.mydroid.client.DisconnectedTip
import com.allan.mydroid.client.HostEndpoint
import com.allan.mydroid.client.api.WsConnectionState
import com.allan.mydroid.client.beans.SelectedFile
import com.au.module_androiduiex.styles.ComposeTypography
import com.au.module_androiduiex.styles.noBackClickable

/**
 * client 发文件到 host 页面（对应 host MyDroidMode.Receiver）。
 */
@Composable
fun ConnectToHostSendScreen(
    viewModel: ConnectToHostSendViewModel,
    endpoint: HostEndpoint,
    titleText: String,
) {
    val uiState by viewModel.uiState.collectAsState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            val files = uris.mapNotNull { uri ->
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
                SelectedFile(uri, name, 0L)
            }
            viewModel.onFilesPicked(files)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Column(modifier = Modifier.fillMaxSize()) {
            ConnectToHostHeader(
                titleText = titleText,
                ip = endpoint.ip,
                httpPort = endpoint.httpPort,
            )

            if (uiState.connectionState is WsConnectionState.Failed) {
                DisconnectedTip(text = stringResource(R.string.connect_to_host_disconnected))
            }

            uiState.leftSpace?.let { space ->
                BasicText(
                    text = stringResource(R.string.storage_remaining) + space,
                    style = ComposeTypography.Font14Desc91,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { picker.launch("*/*") },
                    modifier = Modifier.weight(1f),
                ) { Text(text = stringResource(R.string.select_files_to_send)) }

                if (uiState.uploading) {
                    Button(
                        onClick = { viewModel.cancelUpload() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                    ) { Text(text = stringResource(R.string.connect_to_host_cancel_upload)) }
                } else {
                    Button(
                        onClick = { viewModel.startUpload() },
                        modifier = Modifier.weight(1f),
                        enabled = uiState.selectedFiles.isNotEmpty(),
                    ) { Text(text = stringResource(R.string.connect_to_host_start_upload)) }
                }
            }

            if (uiState.uploading) {
                LinearProgressIndicator(
                    progress = { uiState.progress },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(4.dp),
                )
                uiState.currentUploadingName?.let { name ->
                    BasicText(
                        text = stringResource(R.string.connect_to_host_uploading) + ": " + name,
                        style = ComposeTypography.Font14Desc91,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.size(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uiState.selectedFiles, key = { it.uri }) { file ->
                    SelectedFileRow(file = file, onRemove = { viewModel.removeSelectedFile(file.uri) })
                }
                if (uiState.doneFiles.isNotEmpty()) {
                    item {
                        BasicText(
                            text = stringResource(R.string.connect_to_host_upload_done),
                            style = ComposeTypography.Font16M,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    }
                    items(uiState.doneFiles, key = { it }) { name ->
                        BasicText(
                            text = "✓ $name",
                            style = ComposeTypography.Font14sp,
                            modifier = Modifier.padding(vertical = 2.dp),
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
private fun SelectedFileRow(file: SelectedFile, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = file.name,
                style = ComposeTypography.Font14sp,
            )
            if (file.size > 0) {
                BasicText(
                    text = formatBytes(file.size),
                    style = ComposeTypography.Font14DescC0,
                )
            }
        }
        BasicText(
            text = "×",
            style = ComposeTypography.Font16B.copy(textAlign = TextAlign.Center),
            modifier = Modifier
                .size(32.dp)
                .background(Color(0x12000000), RoundedCornerShape(16.dp))
                .noBackClickable { onRemove() }
                .padding(8.dp),
        )
    }
}

private fun formatBytes(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes >= gb -> String.format("%.2fGB", bytes / gb)
        bytes >= mb -> String.format("%.2fMB", bytes / mb)
        bytes >= kb -> String.format("%.2fKB", bytes / kb)
        else -> "${bytes}B"
    }
}
