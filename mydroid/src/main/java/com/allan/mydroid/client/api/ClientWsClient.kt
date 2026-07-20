package com.allan.mydroid.client.api

import com.allan.mydroid.api.Api
import com.allan.mydroid.api.WSApisConst.Companion.API_WS_CLIENT_INIT_CALLBACK
import com.allan.mydroid.api.WSApisConst.Companion.API_WS_LEFT_SPACE
import com.allan.mydroid.api.WSApisConst.Companion.API_WS_PING
import com.allan.mydroid.api.WSApisConst.Companion.API_WS_INIT
import com.allan.mydroid.api.WSApisConst.Companion.API_WS_TEXT_CHAT_SEND
import com.allan.mydroid.api.WSApisConst.Companion.API_WS_TEXT_CHAT_CALLBACK
import com.allan.mydroid.beans.wsdata.LeftSpaceData
import com.allan.mydroid.beans.wsdata.MyDroidModeData
import com.allan.mydroid.beans.wsdata.TextChatWsData
import com.allan.mydroid.client.HostEndpoint
import com.au.module_android.log.logdNoFile
import com.au.module_android.log.loge
import com.au.module_gson.fromGson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import kotlin.time.Duration.Companion.seconds

/**
 * client 端 WebSocket 客户端。Koin factory——每次注入新建实例，随 ViewModel onCleared 自然释放。
 * 内部用 [Api.connectWSServer] 复用 OkHttp；包含 12s 应用层 c_ping 心跳与指数退避重连（1/2/4/8s 最多 4 次）。
 */
class ClientWsClient {
    private val scope: CoroutineScope = MainScope()

    @Volatile
    private var webSocket: WebSocket? = null
    private var endpoint: HostEndpoint? = null

    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0

    private val _connectionStateFlow = MutableStateFlow<WsConnectionState>(WsConnectionState.Disconnected)
    val connectionStateFlow: StateFlow<WsConnectionState> = _connectionStateFlow.asStateFlow()

    private val _incomingFrameFlow = MutableSharedFlow<WsFrame>(extraBufferCapacity = 16)
    val incomingFrameFlow: SharedFlow<WsFrame> = _incomingFrameFlow.asSharedFlow()

    fun connect(endpoint: HostEndpoint) {
        this.endpoint = endpoint
        reconnectAttempt = 0
        doConnect(endpoint)
    }

    private fun doConnect(endpoint: HostEndpoint) {
        _connectionStateFlow.value = WsConnectionState.Connecting
        webSocket?.close(1000, "reconnect")
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                logdNoFile { "client ws onOpen" }
                reconnectAttempt = 0
                _connectionStateFlow.value = WsConnectionState.Connected
                sendClientInit()
                startHeartbeat()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                logdNoFile { "client ws onMessage: $text" }
                parseFrame(text)?.let { _incomingFrameFlow.tryEmit(it) }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                logdNoFile { "client ws onClosed: $code $reason" }
                handleDisconnect("onClosed $code $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                loge { "client ws onFailure: ${t.message}" }
                handleDisconnect("onFailure ${t.message}")
            }
        }
        webSocket = Api.connectWSServer(endpoint.ip, endpoint.wsPort, listener)
    }

    private fun sendClientInit() {
        val json = JSONObject().apply {
            put("api", API_WS_INIT)
            put("wsName", randomWsName())
            put("platform", "android")
        }.toString()
        webSocket?.send(json)
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(12.seconds)
                sendPing()
            }
        }
    }

    fun sendPing() {
        val json = JSONObject().apply { put("api", API_WS_PING) }.toString()
        webSocket?.send(json)
    }

    fun sendTextChat(textBase64: String, timestamp: Long, iconColor: String) {
        val json = JSONObject().apply {
            put("api", API_WS_TEXT_CHAT_SEND)
            put("textBase64", textBase64)
            put("timestamp", timestamp)
            put("iconColor", iconColor)
        }.toString()
        webSocket?.send(json)
    }

    private fun handleDisconnect(reason: String) {
        heartbeatJob?.cancel()
        _connectionStateFlow.value = WsConnectionState.Connecting
        scheduleReconnect(reason)
    }

    private fun scheduleReconnect(reason: String) {
        val ep = endpoint ?: return
        if (reconnectAttempt >= 4) {
            loge { "client ws reconnect failed after 4 attempts: $reason" }
            _connectionStateFlow.value = WsConnectionState.Failed
            return
        }
        val delaySec = 1L shl reconnectAttempt // 1, 2, 4, 8
        reconnectJob?.cancel()
        reconnectJob = scope.launch(Dispatchers.IO) {
            delay(delaySec.seconds)
            reconnectAttempt++
            logdNoFile { "client ws reconnect #$reconnectAttempt after ${delaySec}s ($reason)" }
            doConnect(ep)
        }
    }

    fun close() {
        reconnectJob?.cancel()
        heartbeatJob?.cancel()
        try {
            webSocket?.close(1000, "user close")
        } catch (e: Exception) {
            loge { "ws close error: ${e.message}" }
        }
        webSocket = null
        _connectionStateFlow.value = WsConnectionState.Disconnected
        scope.cancel()
    }

    private fun parseFrame(text: String): WsFrame? {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val api = json.optString("api")
        if (api.isNullOrEmpty()) return WsFrame.Unknown("", text)
        // data 可能是对象或字符串，统一用 toString 再 fromGson 解析
        val dataStr = json.opt("data")?.toString() ?: return WsFrame.Unknown(api, text)
        return when (api) {
            API_WS_CLIENT_INIT_CALLBACK -> {
                val data = dataStr.fromGson<MyDroidModeData>()
                WsFrame.ClientInitBack(
                    myDroidMode = data?.myDroidMode ?: "",
                    clientName = data?.clientName ?: "",
                    color = data?.color ?: ""
                )
            }
            API_WS_LEFT_SPACE -> {
                val data = dataStr.fromGson<LeftSpaceData>()
                WsFrame.LeftSpace(leftSpaceStr = data?.leftSpace ?: "")
            }
            API_WS_TEXT_CHAT_CALLBACK -> {
                val data = dataStr.fromGson<TextChatWsData>()
                WsFrame.TextChat(
                    textBase64 = data?.textBase64 ?: "",
                    ip = data?.ip ?: "",
                    host = data?.host ?: "",
                    timestamp = data?.timestamp ?: 0L,
                    iconColor = data?.iconColor ?: ""
                )
            }
            else -> WsFrame.Unknown(api, text)
        }
    }

    private fun randomWsName(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}

/** WS 收到的应用层帧。 */
sealed class WsFrame {
    /** host 响应 c_wsInit，回 host 当前 mode / clientName / 分配的 color。 */
    data class ClientInitBack(val myDroidMode: String, val clientName: String, val color: String) : WsFrame()

    /** host 主动推送剩余空间。所有模式都会收到，Chat 模式应忽略。 */
    data class LeftSpace(val leftSpaceStr: String) : WsFrame()

    /** host 广播聊天消息（含自己发的回环）。 */
    data class TextChat(
        val textBase64: String,
        val ip: String,
        val host: String,
        val timestamp: Long,
        val iconColor: String
    ) : WsFrame()

    /** 未知 api，备查。 */
    data class Unknown(val api: String, val rawJson: String) : WsFrame()
}

sealed class WsConnectionState {
    object Disconnected : WsConnectionState()
    object Connecting : WsConnectionState()
    object Connected : WsConnectionState()
    object Failed : WsConnectionState()
}
