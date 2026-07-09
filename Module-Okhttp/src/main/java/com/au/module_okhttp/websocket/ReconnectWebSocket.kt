package com.au.module_okhttp.websocket

import android.annotation.SuppressLint
import com.au.module_android.Globals
import com.au.module_android.log.logEx
import com.au.module_android.log.logdNoFile
import com.au.module_android.utils.ignoreError
import com.au.module_okhttp.creator.myTrustAll
import com.au.module_okhttp.websocket.IWebsocketConnectedListener.Reason
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

@SuppressLint("TrustAllX509TrustManager")
internal open class ReconnectWebSocket(
    override val nameTag: String,
    override val ip: String,
    private val url: String,
    private val headers: Map<String, String> = emptyMap(),
    val maxReconnectAttempts: Int,
) : IWebSocket, IWebSocketClose {
    override fun toString(): String {
        return "Reconnect($nameTag, $ip, $url)"
    }

    /**
     * 外部设置：如果msgID能解析就不会走这里。用于额外解析。
     */
    internal var extraParser: IExtraParser? = null

    private val reconnectScope = Globals.createBackAppScope("ReconnectWS Coroutine catch: ")

    /**
     * 可以用于子类或者外部监听状态的变化
     */
    private var onConnectedListener: IWebsocketConnectedListener? = null

    private var onceStateConnected: ((IWebSocket) -> Unit)? = null

    /** 当前连接状态 ...*/
    @Volatile
    private var _state = IWebSocket.State.DISCONNECTED

    override val state: IWebSocket.State
        get() = _state

    fun setConnectedListener(listener: IWebsocketConnectedListener?) {
        onConnectedListener = listener
    }

    protected open fun createOkHttpClient() = OkHttpClient.Builder()
        .myTrustAll("TLSv1.3")
        .retryOnConnectionFailure(true)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    protected var _client : OkHttpClient? = null
    private val client: OkHttpClient
        get() {
            val client = _client ?: createOkHttpClient().apply { _client = this }
            return client
        }

    private var webSocket: WebSocket? = null

    private var shouldReconnect = true
    private var connectAttempts = 0
    private val reconnectDelays = listOf(5000L, 15000L, 30000L, 120000L, 300000L) // 5秒, 15秒, 30秒, 2分, 5分
    private val quickReconnectDelay = 3000L

    private class MsgCallbackValue(val callback:(text: String, jo: JSONObject, mode: SendMsgCallbackMode) -> Unit,
                                         val job: Job?) {
        fun run(text: String, jo: JSONObject, mode: SendMsgCallbackMode) {
            callback(text, jo, mode)
            if(job != null && !job.isCancelled) job.cancel()
        }
    }

    private val mMsgCallbacks = ConcurrentHashMap<String, MsgCallbackValue>()

    override fun connect(onceStateConnected: ((IWebSocket) -> Unit)?) {
        this.onceStateConnected = onceStateConnected

        shouldReconnect = true
        internalConnect()
    }

    private fun internalConnect() {
        // 关闭旧连接，防止多重试导致的多实例并存
        val oldWS = webSocket
        webSocket = null
        oldWS?.cancel()

        connectAttempts++
        logdNoFile { "internal connect $url" }
        val builder = Request.Builder()
            .url(url)
        headers.forEach { (k, v) ->
            builder.addHeader(k, v)
        }
        val request = builder.build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // 校验是否是当前最新的socket
                if (webSocket !== this@ReconnectWebSocket.webSocket) return

                if (_state != IWebSocket.State.CONNECTED) {
                    _state = IWebSocket.State.CONNECTED
                    onConnectedListener?.onConnected(this@ReconnectWebSocket, true, Reason.SUCCESS)

                    onceStateConnected?.invoke(this@ReconnectWebSocket)
                    onceStateConnected = null //用完即焚
                }
                connectAttempts = 0
                logdNoFile { "WebSocket 连接成功" }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (webSocket !== this@ReconnectWebSocket.webSocket) return
                logdNoFile { "on Message: $text" }
                val jo = ignoreError { JSONObject(text) } ?: return
                val type = jo.optString("type")
                when (type) {
                    "event" -> {
                        extraParser?.onExtraParse(nameTag, text, jo)
                        return
                    }
                    "response" -> {
                        val id = jo.optString("id")
                        if (id.isNotEmpty()) {
                            val value = mMsgCallbacks.remove(id)
                            //如果解析到了callback，则无需再解析；否则交给外部解析
                            if (value != null) {
                                value.run(text, jo, SendMsgCallbackMode.SUCCESS)
                                return
                            }
                        }
                    }
                }

                //其他情况都交给外部解析
                extraParser?.onExtraParse(nameTag, text, jo)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (webSocket !== this@ReconnectWebSocket.webSocket) return

                logEx(throwable = t) { "连接失败: ${t.message}" }
                val callbacks = ArrayList(mMsgCallbacks.entries)
                mMsgCallbacks.clear()

                if (callbacks.isNotEmpty()) {
                    val jo = JSONObject().apply {
                        put("message", t.message ?: "")
                    }
                    val text = jo.toString()
                    callbacks.forEach { (_, value) ->
                        value.run(text, jo, SendMsgCallbackMode.FAIL)
                    }
                }
                scheduleReconnect()
                onceStateConnected = null //用完即焚
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (webSocket !== this@ReconnectWebSocket.webSocket) return

                logdNoFile { "连接关闭" }
                val callbacks = ArrayList(mMsgCallbacks.entries)
                mMsgCallbacks.clear()
                val jo = JSONObject().apply {
                    put("message", reason)
                }
                val text = jo.toString()
                if (callbacks.isNotEmpty()) {
                    callbacks.forEach { (_, value) ->
                        value.run(text, jo, SendMsgCallbackMode.CLOSE)
                    }
                }
                scheduleReconnect()
                onceStateConnected = null //用完即焚
            }
        }

        webSocket = client.newWebSocket(request, listener)
    }

    /**
     * 安排WebSocket重连
     * @return 如果成功安排了重连则返回true，否则返回false
     */
    private fun scheduleReconnect() : Boolean {
        if (!shouldReconnect) return false

        //重试用完。maxReconnectAttempts 为0无限重试。当前连接设备的处理逻辑需要无限重连，时间reconnectDelays降级重连。
        if (maxReconnectAttempts >= 1 && connectAttempts > maxReconnectAttempts) {
            shouldReconnect = false
            connectAttempts = 0
            //不论如何都通知, 不管之前是否已经连接
            _state = IWebSocket.State.DISCONNECTED
            onConnectedListener?.onConnected(this@ReconnectWebSocket, false, Reason.MAX_ATTEMPTS_EXCEEDED)
            onceStateConnected = null //用完即焚
            logdNoFile { "达到最大重连次数，停止重连" }
            return false
        } else {
            _state = IWebSocket.State.RECONNECTING
            onConnectedListener?.onDisconnectedAndTrying(this@ReconnectWebSocket)
        }

        val currentDelay = if (maxReconnectAttempts > 0)
            quickReconnectDelay
        else
            reconnectDelays[minOf(connectAttempts, reconnectDelays.size - 1)]

        reconnectScope.launch {
            delay(currentDelay.milliseconds)
            if (_state == IWebSocket.State.RECONNECTING && shouldReconnect) {
                logdNoFile { "尝试重连... (第${connectAttempts + 1}次，延迟${currentDelay/1000}秒)" }
                internalConnect()
            }
        }
        return true
    }

    /**
     * 断开连接
     * @param isDestroy 是否是销毁。主要就是将监听器置空
     */
    override fun disconnect(isDestroy: Boolean) {
        shouldReconnect = false

        reconnectScope.cancel()
        //   1000~1015 https://datatracker.ietf.org/doc/html/rfc6455#section-7.4
        webSocket?.close(1000, "用户手动断开")
        _state = IWebSocket.State.DISCONNECTED
        onConnectedListener?.onConnected(this@ReconnectWebSocket, false, Reason.DISCONNECT_BY_CALLER)
        onConnectedListener = null
        onceStateConnected = null
        if (isDestroy) {
            onDestroyed()
        }
    }

    override fun sendMsg(message: String): Boolean {
        return if (_state == IWebSocket.State.CONNECTED) {
            val r = webSocket?.send(message)
            r == true
        } else {
            false
        }
    }

    override fun sendMsg(message: String, msgIDInMessage: String,
                         timeoutTs: Long,
                         successCallback: (text: String, jo: JSONObject, mode: SendMsgCallbackMode) -> Unit) : Boolean {
        return if (_state == IWebSocket.State.CONNECTED) {
            val job = if (timeoutTs > 0) {
                //这里使用全局的scope来做，不使用本地的scope，而且不会被主动cancel，哪怕断开后，也要保证有回调
                Globals.backgroundScope.launch {
                    delay(timeoutTs.milliseconds)
                    mMsgCallbacks.remove(msgIDInMessage)?.run("{}", JSONObject(), SendMsgCallbackMode.TIMEOUT)
                }
            } else {
                null
            }
            mMsgCallbacks[msgIDInMessage] = MsgCallbackValue(successCallback, job)
            val r = webSocket?.send(message)
            if (r != true) {
                job?.cancel()
                mMsgCallbacks.remove(msgIDInMessage)?.run("{}", JSONObject(), SendMsgCallbackMode.FAIL)
                return false
            }
            true
        } else {
            false
        }
    }

    protected open fun onDestroyed() {
        //销毁以后，整个类其实都需要立刻释放
        _client?.apply {
            dispatcher.executorService.shutdown()
            connectionPool.evictAll()
            cache?.close()
        }
        _client = null
    }

}
