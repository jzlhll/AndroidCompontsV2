package com.allan.mydroid.client.api

import com.allan.mydroid.api.Api
import com.allan.mydroid.api.MyDroidMode
import com.allan.mydroid.api.WSApisConst.Companion.API_WS_CLIENT_INIT_CALLBACK
import com.allan.mydroid.api.WSApisConst.Companion.API_WS_INIT
import com.allan.mydroid.api.WSApisConst.Companion.API_WS_LEFT_SPACE
import com.allan.mydroid.api.WSApisConst.Companion.API_WS_PING
import com.allan.mydroid.api.WSApisConst.Companion.API_WS_TEXT_CHAT_CALLBACK
import com.allan.mydroid.api.WSApisConst.Companion.API_WS_TEXT_CHAT_SEND
import com.allan.mydroid.beans.wsdata.LeftSpaceData
import com.allan.mydroid.beans.wsdata.MyDroidModeData
import com.allan.mydroid.beans.wsdata.TextChatWsData
import com.allan.mydroid.client.HostEndpoint
import com.au.module_android.log.loge
import com.au.module_gson.fromGson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import kotlin.time.Duration.Companion.milliseconds

/** 页面独占的连接会话：HTTP 校验模式和端口，登记成功后可操作，退出后不再重连。 */
class ClientWsClient {
    private val scope: CoroutineScope = MainScope()
    private var webSocket: WebSocket? = null
    private var endpoint: HostEndpoint? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var initJob: Job? = null
    private var reconnectAttempt = 0
    private var generation = 0L
    private var closed = false
    private var identity: WsFrame.ClientInitBack? = null

    // 同一消费者按收到的顺序处理握手、文本帧和服务端关闭事件。
    private val socketEvents = Channel<SocketEvent>(Channel.UNLIMITED)

    private val _connectionStateFlow = MutableStateFlow<WsConnectionState>(WsConnectionState.Disconnected)
    val connectionStateFlow = _connectionStateFlow.asStateFlow()
    private val _incomingFrameFlow = MutableSharedFlow<WsFrame>(extraBufferCapacity = 64)
    val incomingFrameFlow = _incomingFrameFlow.asSharedFlow()

    init {
        scope.launch {
            for (event in socketEvents) {
                if (closed || event.token != generation) continue
                when (event) {
                    is SocketEvent.Message -> handleFrame(event.token, event.text)
                    is SocketEvent.Disconnected -> handleDisconnect(event.token)
                }
            }
        }
    }

    fun connect(endpoint: HostEndpoint) {
        if (closed) return
        this.endpoint = endpoint
        reconnectAttempt = 0
        reconnectJob?.cancel()
        invalidateSocket()
        doConnect()
    }

    fun reconnect() {
        if (_connectionStateFlow.value == WsConnectionState.ModeChanged) return
        endpoint?.let { connect(it) }
    }

    private fun invalidateSocket() {
        generation++
        identity = null
        initJob?.cancel()
        heartbeatJob?.cancel()
        webSocket?.cancel()
        webSocket = null
    }

    private fun doConnect() {
        val ep = endpoint ?: return
        val token = generation
        _connectionStateFlow.value = if (reconnectAttempt == 0) WsConnectionState.Connecting else WsConnectionState.Reconnecting
        reconnectJob = scope.launch {
            try {
                val baseUrl = ClientApi.baseUrl(ep.ip, ep.httpPort)
                val mode = ClientApi.fetchMode(baseUrl)
                if (mode != ep.mode || mode == MyDroidMode.None) {
                    _connectionStateFlow.value = WsConnectionState.ModeChanged
                    return@launch
                }
                val info = withTimeoutOrNull(5000.milliseconds) { ClientApi.fetchWsIpPort(baseUrl) }
                    ?: throw java.io.IOException("Fetch websocket port timed out")
                if (closed || token != generation) return@launch
                val listener = object : WebSocketListener() {
                    override fun onOpen(socket: WebSocket, response: okhttp3.Response) {
                        scope.launch {
                            if (closed || token != generation) return@launch
                            val json = JSONObject().put("api", API_WS_INIT).put("platform", "android").toString()
                            if (!socket.send(json)) handleDisconnect(token)
                        }
                    }

                    override fun onMessage(socket: WebSocket, text: String) {
                        socketEvents.trySend(SocketEvent.Message(token, text))
                    }

                    override fun onClosing(socket: WebSocket, code: Int, reason: String) {
                        socket.close(code, reason)
                        socketEvents.trySend(SocketEvent.Disconnected(token))
                    }

                    override fun onClosed(socket: WebSocket, code: Int, reason: String) {
                        socketEvents.trySend(SocketEvent.Disconnected(token))
                    }

                    override fun onFailure(socket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                        loge { "Client ws failed: ${t.message}" }
                        socketEvents.trySend(SocketEvent.Disconnected(token))
                    }
                }
                webSocket = Api.connectWSServer(ep.ip, info.port, listener)
                initJob = scope.launch {
                    delay(5000.milliseconds)
                    handleDisconnect(token)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loge { "Client connection failed: ${e.message}" }
                handleDisconnect(token)
            }
        }
    }

    private suspend fun handleFrame(token: Long, text: String) {
        val frame = try {
            parseFrame(text) ?: return
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loge { "Client frame parse failed: ${e.message}" }
            return
        }
        when (frame) {
            is WsFrame.ClientInitBack -> {
                if (frame.clientName.isBlank() || identity != null) return
                initJob?.cancel()
                identity = frame
                reconnectAttempt = 0
                _connectionStateFlow.value = WsConnectionState.Connected
                startHeartbeat(token)
            }
            is WsFrame.TextChat -> if (identity == null) return
            else -> Unit
        }
        _incomingFrameFlow.emit(frame)
    }

    private fun startHeartbeat(token: Long) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(12000.milliseconds)
                sendPing()
                if (token != generation) return@launch
            }
        }
    }

    fun sendPing() {
        if (webSocket?.send(JSONObject().put("api", API_WS_PING).toString()) != true) {
            handleDisconnect(generation)
        }
    }

    fun sendTextChat(textBase64: String, timestamp: Long): WsFrame.ClientInitBack? {
        val sender = identity ?: return null
        if (_connectionStateFlow.value != WsConnectionState.Connected) return null
        val sent = webSocket?.send(JSONObject().apply {
            put("api", API_WS_TEXT_CHAT_SEND)
            put("textBase64", textBase64)
            put("timestamp", timestamp)
            put("iconColor", sender.color)
        }.toString()) == true
        if (sent) return sender
        handleDisconnect(generation)
        return null
    }

    private fun handleDisconnect(token: Long) {
        if (closed || token != generation || _connectionStateFlow.value == WsConnectionState.ModeChanged) return
        invalidateSocket()
        if (reconnectAttempt >= 4) {
            _connectionStateFlow.value = WsConnectionState.Failed
            return
        }
        _connectionStateFlow.value = WsConnectionState.Reconnecting
        val waitMs = 1000L shl reconnectAttempt
        reconnectAttempt++
        reconnectJob = scope.launch {
            delay(waitMs.milliseconds)
            doConnect()
        }
    }

    fun close() {
        closed = true
        reconnectJob?.cancel()
        invalidateSocket()
        _connectionStateFlow.value = WsConnectionState.Disconnected
        socketEvents.cancel()
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
                    iconColor = data?.iconColor ?: "",
                    replayed = data?.replayed ?: false,
                )
            }
            else -> WsFrame.Unknown(api, text)
        }
    }

    private sealed interface SocketEvent {
        val token: Long

        data class Message(override val token: Long, val text: String) : SocketEvent
        data class Disconnected(override val token: Long) : SocketEvent
    }
}

/** WS 收到的应用层帧。 */
sealed class WsFrame {
    /** host 响应 c_wsInit，回 host 当前 mode / clientName / 分配的 color。 */
    data class ClientInitBack(val myDroidMode: String, val clientName: String, val color: String) : WsFrame()

    /** host 主动推送剩余空间。所有模式都会收到，Chat 模式应忽略。 */
    data class LeftSpace(val leftSpaceStr: String) : WsFrame()

    /** 主机推送聊天消息或连接时的历史。 */
    data class TextChat(
        val textBase64: String,
        val ip: String,
        val host: String,
        val timestamp: Long,
        val iconColor: String,
        val replayed: Boolean,
    ) : WsFrame()

    /** 未知 api，备查。 */
    data class Unknown(val api: String, val rawJson: String) : WsFrame()
}

sealed class WsConnectionState {
    object Disconnected : WsConnectionState()
    object Connecting : WsConnectionState()
    object Connected : WsConnectionState()
    object Failed : WsConnectionState()
    object Reconnecting : WsConnectionState()
    object ModeChanged : WsConnectionState()
}
