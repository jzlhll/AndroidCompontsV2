package com.allan.mydroid.client.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.allan.mydroid.R
import com.allan.mydroid.client.ConnectToHostHeader
import com.allan.mydroid.client.DisconnectedTip
import com.allan.mydroid.client.HostEndpoint
import com.allan.mydroid.client.api.WsConnectionState
import com.allan.mydroid.client.beans.ChatMessage
import com.au.module_androiduiex.styles.ComposeTypography
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * client 与 host 文本对话页面（对应 host MyDroidMode.TextChat）。
 */
@Composable
fun ConnectToHostChatScreen(
    viewModel: ConnectToHostChatViewModel,
    endpoint: HostEndpoint,
    titleText: String,
) {
    val uiState by viewModel.uiState.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFFAFAFA)),
    ) {
        ConnectToHostHeader(
            titleText = titleText,
            ip = endpoint.ip,
            httpPort = endpoint.httpPort,
        )

        if (uiState.connectionState is WsConnectionState.Failed) {
            DisconnectedTip(text = stringResource(R.string.connect_to_host_disconnected))
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(uiState.messages, key = { it.timestamp.toString() + it.isMe }) { msg ->
                MessageRow(msg = msg, onLongPress = { copyToClipboard(context, msg.text) })
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .background(Color.White)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(text = stringResource(R.string.text_chat_input_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                singleLine = false,
                maxLines = 4,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (input.isNotBlank()) {
                        viewModel.sendText(input)
                        input = ""
                    }
                },
                enabled = input.isNotBlank() && uiState.connectionState is WsConnectionState.Connected,
            ) { Text(text = stringResource(R.string.send)) }
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
private fun MessageRow(msg: ChatMessage, onLongPress: () -> Unit) {
    val alignment = if (msg.isMe) Alignment.End else Alignment.Start
    val bubbleColor = if (msg.isMe) Color(0xFF1E88E5) else Color(0xFFE0E0E0)
    val textColor = if (msg.isMe) Color.White else Color.Black
    val time = remember(msg.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!msg.isMe) {
                BasicText(
                    text = "🌟",
                    style = TextStyle(fontSize = 16.sp),
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Box(
                modifier = Modifier
                    .background(Color(0xFFF0F0F0), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                BasicText(
                    text = msg.ip,
                    style = ComposeTypography.Font14DescC0,
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .background(bubbleColor, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .pointerInput(msg.timestamp) {
                        detectTapGestures(onLongPress = { onLongPress() })
                    },
            ) {
                BasicText(
                    text = msg.text,
                    style = TextStyle(color = textColor, fontSize = 15.sp),
                )
            }
        }
        BasicText(
            text = time,
            style = ComposeTypography.Font14DescC0,
            modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("text", text))
}
