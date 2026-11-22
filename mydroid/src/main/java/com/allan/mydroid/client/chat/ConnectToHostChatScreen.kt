package com.allan.mydroid.client.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.isVisible
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.allan.mydroid.R
import com.allan.mydroid.beans.wsdata.TextChatMessageBean
import com.allan.mydroid.client.ConnectionStatus
import com.allan.mydroid.client.HostEndpoint
import com.allan.mydroid.client.api.WsConnectionState
import com.allan.mydroid.databinding.LayoutTextChatSettingsBinding
import com.allan.mydroid.state.TextChatRow
import com.allan.mydroid.views.chat.bindChatSettings
import com.allan.mydroid.views.chat.displayText
import com.au.module_androidui.R as AndroidUiR
import com.au.module_androidui.toast.MyToastUtil
import com.au.module_androiduiex.preview.AppPreview
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
    settingsVisible: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val rows by viewModel.timelineFlow.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val inputShape = RoundedCornerShape(20.dp)
    val accentColor = colorResource(R.color.text_chat_accent)
    val accentTextColor = colorResource(R.color.text_chat_accent_text)
    val canSend = input.isNotBlank() && uiState.canSend && !settingsVisible

    LaunchedEffect(rows.lastOrNull()?.id) {
        if (rows.isNotEmpty()) {
            listState.animateScrollToItem(rows.lastIndex)
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
            .background(colorResource(R.color.text_chat_background))
            .then(
                if (settingsVisible) {
                    Modifier.clearAndSetSemantics { }
                        .focusProperties { canFocus = false }
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                                }
                            }
                        }
                } else {
                    Modifier
                },
            ),
    ) {
        Box(Modifier.fillMaxWidth().statusBarsPadding()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 56.dp)
                    .padding(top = 12.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BasicText(text = titleText, style = ComposeTypography.Font20M)
                if (uiState.canSend) {
                    BasicText(
                        text = uiState.selfName.orEmpty(),
                        style = ComposeTypography.Font12DescC0,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    BasicText(
                        text = stringResource(R.string.connect_to_host_chat_connected, endpoint.address),
                        style = ComposeTypography.Font14Desc91.copy(textAlign = TextAlign.Center),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            IconButton(
                onClick = {
                    focusManager.clearFocus(force = true)
                    onOpenSettings()
                },
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 4.dp),
                enabled = !settingsVisible,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_text_chat_settings),
                    contentDescription = stringResource(R.string.text_chat_settings),
                    tint = colorResource(R.color.text_chat_text),
                )
            }
        }

        if (uiState.connectionState != WsConnectionState.Connected) {
            ConnectionStatus(uiState.connectionState, viewModel::reconnect)
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(rows, key = { it.id }) { row ->
                when (row) {
                    is TextChatRow.Message -> MessageRow(row, onLongPress = { copyToClipboard(context, row.bean.text) })
                    is TextChatRow.Divider -> DividerRow(row)
                }
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
                enabled = !settingsVisible,
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
                enabled = canSend,
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
                    color = if (canSend) {
                        accentTextColor
                    } else {
                        accentTextColor.copy(alpha = 0.45f)
                    },
                )
            }
        }
    }

    uiState.error?.takeIf { !settingsVisible }?.let { err ->
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
fun ConnectToHostChatSettings(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val close by rememberUpdatedState(onClose)
    AndroidView(
        factory = { context ->
            val settings = LayoutTextChatSettingsBinding.inflate(LayoutInflater.from(context))
            settings.bindChatSettings(onClose = { close() })
            settings.root.apply {
                isVisible = true
                requestFocus()
            }
        },
        modifier = modifier.fillMaxSize()
            .background(colorResource(R.color.text_chat_surface))
            .safeDrawingPadding(),
    )
}

@Composable
private fun DividerRow(row: TextChatRow.Divider) {
    val lineColor = colorResource(R.color.text_chat_input_border)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val maxTextWidth = maxWidth * 0.8f
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HorizontalDivider(Modifier.weight(1f), color = lineColor)
            Text(
                text = row.displayText(LocalContext.current),
                modifier = Modifier.widthIn(max = maxTextWidth),
                color = colorResource(R.color.text_chat_text_secondary),
                style = ComposeTypography.Font12DescC0,
                textAlign = TextAlign.Center,
            )
            HorizontalDivider(Modifier.weight(1f), color = lineColor)
        }
    }
}

@Composable
private fun MessageRow(msg: TextChatRow.Message, onLongPress: () -> Unit) {
    val alignment = if (msg.isMine) Alignment.End else Alignment.Start
    val bubbleColor = if (msg.isMine) Color(0xFF1E88E5) else Color(0xFFE0E0E0)
    val time = remember(msg.timestamp) {
        SimpleDateFormat("MM-dd HH:mm", Locale.ROOT).format(Date(msg.timestamp))
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!msg.isMine) {
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
                    text = msg.bean.ip,
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
                    text = msg.bean.text,
                    style = if (msg.isMine) ComposeTypography.Font16MWhite else ComposeTypography.Font16,
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
    MyToastUtil.toastOnBottom(
        context.getString(R.string.text_chat_copy_success),
        iconResId = AndroidUiR.drawable.ic_successful,
    )
}


@Preview
@Composable
private fun ChatMessagePreview() {
    val bean = TextChatMessageBean("文件已收到", "192.168.1.2", "", 0, "")
    AppPreview { MessageRow(TextChatRow.Message(0, bean, isMine = false), {}) }
}
