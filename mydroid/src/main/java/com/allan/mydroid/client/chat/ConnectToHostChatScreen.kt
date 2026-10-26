package com.allan.mydroid.client.chat

import androidx.compose.foundation.layout.*
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.tooling.preview.Preview
import com.au.module_androiduiex.preview.AppPreview
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.allan.mydroid.R
import com.allan.mydroid.client.ConnectToHostHeader
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val inputShape = RoundedCornerShape(20.dp)
    val accentColor = colorResource(R.color.text_chat_accent)
    val accentTextColor = colorResource(R.color.text_chat_accent_text)

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(colorResource(R.color.text_chat_background)),
    ) {
        ConnectToHostHeader(
            titleText = titleText,
            ip = endpoint.ip,
            httpPort = endpoint.httpPort,
        )

        com.allan.mydroid.client.ConnectionStatus(uiState.connectionState, viewModel::reconnect)

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(uiState.messages, key = { "${it.ip}:${it.timestamp}:${it.text}" }) { msg ->
                MessageRow(msg = msg, onLongPress = { copyToClipboard(context, msg.text) })
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colorResource(R.color.text_chat_surface))
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp, max = 120.dp)
                    .background(colorResource(R.color.text_chat_input_bg), inputShape)
                    .border(1.dp, colorResource(R.color.text_chat_input_border), inputShape)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                cursorBrush = SolidColor(accentColor),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (viewModel.sendText(input)) input = "" }),
                textStyle = ComposeTypography.Font16,
                singleLine = false,
                maxLines = 4,
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 28.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (input.isEmpty()) {
                            Text(
                                text = stringResource(R.string.text_chat_input_hint),
                                style = ComposeTypography.Font16,
                                color = colorResource(R.color.text_chat_input_hint),
                            )
                        }
                        innerTextField()
                    }
                },
            )
            Spacer(modifier = Modifier.width(10.dp))
            Button(
                onClick = {
                    if (viewModel.sendText(input)) input = ""
                },
                modifier = Modifier.width(72.dp).height(44.dp),
                enabled = input.isNotBlank() && uiState.connectionState is WsConnectionState.Connected,
                shape = inputShape,
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = accentTextColor,
                    disabledContainerColor = accentColor.copy(alpha = 0.45f),
                    disabledContentColor = accentTextColor.copy(alpha = 0.45f),
                ),
            ) {
                Text(
                    text = stringResource(R.string.send),
                    style = ComposeTypography.Font14sp,
                    color = if (input.isNotBlank() && uiState.connectionState is WsConnectionState.Connected) {
                        accentTextColor
                    } else {
                        accentTextColor.copy(alpha = 0.45f)
                    },
                )
            }
        }
    }

    uiState.error?.let { err ->
        AlertDialog(
            onDismissRequest = { viewModel.consumeError() },
            title = { Text(text = stringResource(R.string.tips), style = ComposeTypography.Font14sp) },
            text = { Text(text = err, style = ComposeTypography.Font14sp) },
            confirmButton = {
                TextButton(onClick = { viewModel.consumeError() }) {
                    Text(text = stringResource(R.string.action_confirm), style = ComposeTypography.Font14sp)
                }
            },
        )
    }
}

@Composable
private fun MessageRow(msg: ChatMessage, onLongPress: () -> Unit) {
    val alignment = if (msg.isMe) Alignment.End else Alignment.Start
    val bubbleColor = if (msg.isMe) Color(0xFF1E88E5) else Color(0xFFE0E0E0)
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
                    style = ComposeTypography.Font16,
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
                    style = if (msg.isMe) ComposeTypography.Font16MWhite else ComposeTypography.Font16,
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
    com.au.module_androidui.toast.ToastBuilder().setOnTop().setIcon("success")
        .setMessage(context.getString(R.string.text_chat_copy_success)).toast()
}


@Preview
@Composable
private fun ChatMessagePreview() {
    AppPreview { MessageRow(ChatMessage("文件已收到", false, 0, "", "192.168.1.2"), {}) }
}
