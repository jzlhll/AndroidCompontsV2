package com.allan.mydroid.client.chat

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allan.mydroid.R
import com.allan.mydroid.beans.wsdata.TextChatMessageBean
import com.allan.mydroid.beans.wsdata.getIconColorByIp
import com.allan.mydroid.client.HostEndpoint
import com.allan.mydroid.client.api.ClientWsClient
import com.allan.mydroid.client.api.WsConnectionState
import com.allan.mydroid.client.api.WsFrame
import com.allan.mydroid.state.GlobalTextChatObj
import com.au.module_android.Globals
import com.au.module_android.log.loge
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 管理与主机的文本聊天连接，并将收到和发出的消息保存到本机时间线。 */
class ConnectToHostChatViewModel(
    private val endpoint: HostEndpoint,
    private val wsClient: ClientWsClient,
    private val textChatStore: GlobalTextChatObj,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ConnectToHostChatUiState())
    val uiState = _uiState.asStateFlow()
    val timelineFlow = textChatStore.timelineFlow

    private val _liveMessageCountFlow = MutableStateFlow(0L)
    val liveMessageCountFlow = _liveMessageCountFlow.asStateFlow()

    private var lastSentTimestamp = 0L
    private var clientName: String? = null

    init {
        textChatStore.prepareHistoryForEntry()
        viewModelScope.launch {
            wsClient.connectionStateFlow.collect { state ->
                _uiState.update {
                    if (state == WsConnectionState.Connected) it.copy(connectionState = state)
                    else it.copy(connectionState = state, selfName = null)
                }
            }
        }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            wsClient.incomingFrameFlow.collect { frame ->
                when (frame) {
                    is WsFrame.ClientInitBack -> {
                        // 握手和回放按网络顺序处理，重连时只更新当前连接的客户端名。
                        if (clientName == null) textChatStore.recordJoinedRoom()
                        clientName = frame.clientName
                        _uiState.update { it.copy(selfName = frame.clientName) }
                    }
                    is WsFrame.TextChat -> receiveText(frame)
                    else -> Unit
                }
            }
        }
        wsClient.connect(endpoint)
    }

    fun reconnect() = wsClient.reconnect()

    fun sendText(text: String): Boolean {
        if (text.isBlank() || !_uiState.value.canSend) return false
        val now = System.currentTimeMillis()
        val timestamp = if (now > lastSentTimestamp) now else lastSentTimestamp + 1
        val base64 = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val sender = wsClient.sendTextChat(base64, timestamp)
        if (sender == null) {
            _uiState.update { it.copy(error = Globals.app.getString(R.string.connect_to_host_disconnected)) }
            return false
        }
        lastSentTimestamp = timestamp
        val bean = TextChatMessageBean(
            text = text,
            ip = sender.clientName,
            host = "",
            timestamp = timestamp,
            iconColor = sender.color.ifBlank { getIconColorByIp(sender.clientName) },
        )
        textChatStore.addClientMessage(endpoint.address, bean, isMine = true, onStored = ::onLiveMessageStored)
        return true
    }

    fun consumeError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun receiveText(frame: WsFrame.TextChat) {
        val name = clientName ?: return
        val text = try {
            String(Base64.decode(frame.textBase64, Base64.NO_WRAP), Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            loge { "Decode chat text failed: ${e.message}" }
            return
        }
        if (text.isBlank() || frame.ip.isBlank() || frame.timestamp <= 0) return
        val bean = TextChatMessageBean(text, frame.ip, frame.host, frame.timestamp, frame.iconColor)
        textChatStore.addClientMessage(
            endpoint.address,
            bean,
            isMine = frame.ip == name,
            replayed = frame.replayed,
            onStored = if (frame.replayed) null else ::onLiveMessageStored,
        )
    }

    private fun onLiveMessageStored() {
        viewModelScope.launch {
            _liveMessageCountFlow.update { it + 1 }
        }
    }

    override fun onCleared() {
        wsClient.close()
        super.onCleared()
    }
}

data class ConnectToHostChatUiState(
    val selfName: String? = null,
    val connectionState: WsConnectionState = WsConnectionState.Disconnected,
    val error: String? = null,
) {
    val canSend: Boolean get() = selfName != null && connectionState == WsConnectionState.Connected
}
