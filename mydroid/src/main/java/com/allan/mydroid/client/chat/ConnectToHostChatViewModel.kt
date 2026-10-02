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
 * - 发送成功入队后在本地展示；重连历史按身份、时间与内容去重。
 * - 不持久化，Fragment 退出即清空。
 */
class ConnectToHostChatViewModel(
    private val endpoint: HostEndpoint
) : ViewModel(), KoinComponent {

    private val wsClient: ClientWsClient by inject()

    private val _uiState = MutableStateFlow(ConnectToHostChatUiState(selfColor = getIconColorByIp(endpoint.ip)))
    val uiState: StateFlow<ConnectToHostChatUiState> = _uiState.asStateFlow()

    private var lastSentTimestamp = 0L

    init {
        observeFrames()
        wsClient.connect(endpoint)
    }

    private fun observeFrames() {
        viewModelScope.launch {
            wsClient.connectionStateFlow.collectLatest { state ->
                _uiState.update { it.copy(connectionState = state) }
            }
        }
        viewModelScope.launch {
            wsClient.identityFlow.collect { identity ->
                if (identity != null) _uiState.update { state ->
                    state.copy(selfName = identity.clientName, selfColor = identity.color,
                        messages = state.messages.map { it.copy(isMe = it.ip == identity.clientName) })
                }
            }
        }
        viewModelScope.launch {
            wsClient.incomingFrameFlow.collectLatest { frame ->
                when (frame) {
                    is WsFrame.ClientInitBack -> Unit
                    is WsFrame.TextChat -> {
                        val text = try {
                            String(Base64.decode(frame.textBase64, Base64.NO_WRAP), Charsets.UTF_8)
                        } catch (e: Exception) {
                            loge { "decode chat text failed: ${e.message}" }
                            return@collectLatest
                        }
                        _uiState.update { st ->
                            if (st.messages.any { it.ip == frame.ip && it.timestamp == frame.timestamp && it.text == text }) st
                            else st.copy(messages = st.messages + ChatMessage(text, frame.ip == st.selfName,
                                frame.timestamp, frame.iconColor, frame.ip))
                        }
                    }
                    is WsFrame.LeftSpace -> { /* Chat 模式忽略 leftSpace */ }
                    is WsFrame.Unknown -> Unit
                }
            }
        }
    }

    fun reconnect() = wsClient.reconnect()

    fun sendText(text: String): Boolean {
        if (text.isBlank() || _uiState.value.connectionState != WsConnectionState.Connected) return false
        val now = System.currentTimeMillis()
        val timestamp = if (now > lastSentTimestamp) now else lastSentTimestamp + 1
        lastSentTimestamp = timestamp
        val color = _uiState.value.selfColor
        val base64 = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        if (!wsClient.sendTextChat(base64, timestamp, color)) {
            _uiState.update { it.copy(error = Globals.getString(R.string.connect_to_host_disconnected)) }
            return false
        }
        _uiState.update { st ->
            st.copy(messages = st.messages + ChatMessage(text, true, timestamp, color, st.selfName ?: ""))
        }
        return true
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
