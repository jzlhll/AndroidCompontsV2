package com.allan.mydroid.client

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.allan.mydroid.R
import com.au.module_androiduiex.styles.ComposeTypography

/**
 * client 三模式共用的顶部 Header。复刻 host 三页面提示样式：
 * - 第一行：模式标题
 * - 第二行：不退出，不熄屏
 * - 第三行：目标 ip:port；会话状态由 ConnectionStatus 显示。
 *
 * 注意：沉浸式由 ComposeViewFragment.FullImmersive + statusBarsPadding 处理。
 */
@Composable
fun ConnectToHostHeader(
    titleText: String,
    ip: String,
    httpPort: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(
            text = titleText,
            style = ComposeTypography.Font20M,
        )
        BasicText(
            text = stringResource(R.string.not_close_window).format(""),
            style = ComposeTypography.Font14sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        BasicText(
            text = stringResource(R.string.lan_access_fmt).format(ip, httpPort.toString()),
            style = ComposeTypography.Font14sp,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** 顶部红色断连提示条。 */
@Composable
fun DisconnectedTip(text: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFFFCDD2))
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = ComposeTypography.Font14sp.copy(color = Color(0xFFC62828), textAlign = TextAlign.Center),
        )
    }
}


@Composable
fun ConnectionStatus(state: com.allan.mydroid.client.api.WsConnectionState, onReconnect: () -> Unit) {
    val label = when (state) {
        com.allan.mydroid.client.api.WsConnectionState.Connected -> R.string.transfer_connected
        com.allan.mydroid.client.api.WsConnectionState.Connecting -> R.string.connect_to_host_loading
        com.allan.mydroid.client.api.WsConnectionState.Reconnecting -> R.string.transfer_reconnecting
        com.allan.mydroid.client.api.WsConnectionState.ModeChanged -> R.string.transfer_mode_changed
        com.allan.mydroid.client.api.WsConnectionState.Failed -> R.string.connect_to_host_failed
        com.allan.mydroid.client.api.WsConnectionState.Disconnected -> R.string.connect_to_host_disconnected
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        BasicText(stringResource(label), style = ComposeTypography.Font14Desc91)
        if (state == com.allan.mydroid.client.api.WsConnectionState.Failed) {
            androidx.compose.material3.TextButton(onClick = onReconnect) {
                androidx.compose.material3.Text(stringResource(R.string.connect_to_host_retry), style = ComposeTypography.Font14sp)
            }
        }
    }
}

@Composable
fun TransferMessageDialog(message: String, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        text = { androidx.compose.material3.Text(message, style = ComposeTypography.Font14sp) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) {
            androidx.compose.material3.Text(stringResource(R.string.action_confirm), style = ComposeTypography.Font14sp)
        } }
    )
}


@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun ConnectionPreview() {
    com.au.module_androiduiex.preview.AppPreview {
        Column {
            ConnectToHostHeader(stringResource(R.string.connect_to_host_send_title), "192.168.1.2", 8080)
            ConnectionStatus(com.allan.mydroid.client.api.WsConnectionState.Reconnecting, {})
        }
    }
}
