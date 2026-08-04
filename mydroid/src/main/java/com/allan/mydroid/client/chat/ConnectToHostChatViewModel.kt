package com.allan.mydroid.client.chat

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allan.mydroid.R
import com.allan.mydroid.client.HostEndpoint
import com.allan.mydroid.client.api.ClientWsClient
import com.allan.mydroid.client.api.WsConnectionState
import com.allan.mydroid.client.api.WsFrame
import com.allan.mydroid.beans.wsdata.getIconColorByIp
import com.allan.mydroid.client.beans.ChatMessage
import com.au.module_android.Globals
import com.au.module_android.log.loge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * client 与 host 文本对话模式（对应 host MyDroidMode.TextChat）。
 *
 * - host WS 连上后会主动推送最近一段对话历史，client 端无需主动拉取。
 * - 自己发的消息 host 会广播回来，按 ip + timestamp 去重跳过。
 * - 不持久化，Fragment 退出即清空。
 */
class ConnectToHostChatViewModel(
    private val endpoint: HostEndpoint
) : ViewModel(), KoinComponent {

    private val wsClient: ClientWsClient by inject()

    private val _uiState = MutableStateFlow(ConnectToHostChatUiState(selfColor = getIconColorByIp(endpoint.ip)))
    val uiState: StateFlow<ConnectToHostChatUiState> = _uiState.asStateFlow()

    private val pendingSelfMessages = mutableSetOf<Long>()

    init {
        wsClient.connect(endpoint)
        observeFrames()
    }

    private fun observeFrames() {
        viewModelScope.launch {
            wsClient.connectionStateFlow.collectLatest { state ->
                _uiState.update { it.copy(connectionState = state) }
            }
        }
        viewModelScope.launch {
            wsClient.incomingFrameFlow.collectLatest { frame ->
                when (frame) {
                    is WsFrame.ClientInitBack -> {
                        _uiState.update {
                            it.copy(selfName = frame.clientName, selfColor = getIconColorByIp(frame.clientName))
                        }
                    }
                    is WsFrame.TextChat -> {
                        // 去重：自己发的消息 host 会广播回来
                        if (frame.ip == endpoint.ip && frame.timestamp in pendingSelfMessages) {
                            pendingSelfMessages.remove(frame.timestamp)
                            return@collectLatest
                        }
                        val text = try {
                            String(Base64.decode(frame.textBase64, Base64.NO_WRAP), Charsets.UTF_8)
                        } catch (e: Exception) {
                            loge { "decode chat text failed: ${e.message}" }
                            return@collectLatest
                        }
                        _uiState.update { st ->
                            st.copy(messages = st.messages + ChatMessage(text, isMe = false, frame.timestamp, frame.iconColor, frame.ip))
                        }
                    }
                    is WsFrame.LeftSpace -> { /* Chat 模式忽略 leftSpace */ }
                    is WsFrame.Unknown -> Unit
                }
            }
        }
    }

    fun sendText(text: String) {
        if (text.isBlank()) return
        val timestamp = System.currentTimeMillis()
        val color = _uiState.value.selfColor
        pendingSelfMessages.add(timestamp)
        _uiState.update { st ->
            st.copy(messages = st.messages + ChatMessage(text, isMe = true, timestamp, color, endpoint.ip))
        }
        val base64 = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        try {
            wsClient.sendTextChat(base64, timestamp, color)
        } catch (e: Exception) {
            loge { "sendTextChat failed: ${e.message}" }
            _uiState.update { it.copy(error = Globals.getString(R.string.something_error) + ": ${e.message}") }
        }
    }

    fun consumeError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        wsClient.close()
    }
}

data class ConnectToHostChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val selfColor: String = "",
    val selfName: String? = null,
    val connectionState: WsConnectionState = WsConnectionState.Disconnected,
    val error: String? = null,
)
