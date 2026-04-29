package com.allan.mydroid.views.receiver.nativefiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.allan.mydroid.R
import com.allan.mydroid.beansinner.ShareInHtml
import kotlin.math.max
import kotlin.math.min

@Composable
fun ReceiveFromPhoneNativeScreen(
    state: ReceiveFromPhoneNativeUiState,
    onIpChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onConnectClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onDownloadClick: (ShareInHtml) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(18.dp),
    ) {
        Text(
            text = stringResource(R.string.native_receive_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(18.dp))
        ConnectPanel(
            state = state,
            onIpChange = onIpChange,
            onPortChange = onPortChange,
            onConnectClick = onConnectClick,
        )
        Spacer(modifier = Modifier.height(16.dp))
        state.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (state.isConnected) {
            ConnectedHeader(state, onRefreshClick)
            Spacer(modifier = Modifier.height(12.dp))
            RemoteFileList(state, onDownloadClick)
        }
    }
}

@Composable
private fun ConnectPanel(
    state: ReceiveFromPhoneNativeUiState,
    onIpChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onConnectClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.ip,
                onValueChange = onIpChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.native_receive_ip_hint)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = state.port,
                onValueChange = onPortChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.native_receive_port_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
            Button(
                onClick = onConnectClick,
                enabled = !state.isConnecting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (state.isConnecting) {
                        stringResource(R.string.native_receive_connecting)
                    } else {
                        stringResource(R.string.native_receive_connect)
                    }
                )
            }
        }
    }
}

@Composable
private fun ConnectedHeader(
    state: ReceiveFromPhoneNativeUiState,
    onRefreshClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.native_receive_connected),
                style = MaterialTheme.typography.titleMedium,
            )
            if (state.remoteClientName.isNotEmpty()) {
                Text(
                    text = state.remoteClientName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Button(onClick = onRefreshClick) {
            Text(stringResource(R.string.native_receive_refresh))
        }
    }
}

@Composable
private fun RemoteFileList(
    state: ReceiveFromPhoneNativeUiState,
    onDownloadClick: (ShareInHtml) -> Unit,
) {
    if (state.files.isEmpty()) {
        Text(
            text = stringResource(R.string.native_receive_empty_files),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(state.files, key = { it.uriUuid }) { file ->
            RemoteFileItem(
                file = file,
                downloadState = state.downloads[file.uriUuid],
                onDownloadClick = onDownloadClick,
            )
        }
    }
}

@Composable
private fun RemoteFileItem(
    file: ShareInHtml,
    downloadState: RemoteDownloadState?,
    onDownloadClick: (ShareInHtml) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = file.name ?: "file",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = file.fileSizeStr,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            downloadState?.let {
                if (it.isDownloading) {
                    val progress = min(1f, max(0f, it.progress))
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                it.error?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Button(
                onClick = { onDownloadClick(file) },
                enabled = downloadState?.isDownloading != true,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (downloadState?.isDownloading == true) {
                        stringResource(R.string.native_receive_downloading)
                    } else {
                        stringResource(R.string.download)
                    }
                )
            }
        }
    }
}
