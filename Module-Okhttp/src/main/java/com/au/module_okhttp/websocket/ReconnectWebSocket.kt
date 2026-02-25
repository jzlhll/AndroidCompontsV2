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
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@SuppressLint("TrustAllX509TrustManager")
internal open class ReconnectWebSocket(
    override val nameTag: String,
    override val ip: String,
    private val url: String,
    private val headers: Map<String, String> = emptyMap(),
    val maxReconnectAttempts: Int,
) : IWebSocket {
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
    private var onConnectedListenerRef: WeakReference<IWebsocketConnectedListener>? = null

    /** 当前连接状态 0 未连接 1 已连接, -1重试中...*/
    @Volatile
    private var _state = 0

    override val state: Int
        get() = _state

    fun setConnectedListener(listener: IWebsocketConnectedListener?) {
        onConnectedListenerRef = if (listener == null) {
            null
        } else {
            WeakReference(listener)
        }
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

    private class MsgCallbackValue(val callback:(response: String, mode: SendMsgCallbackMode) -> Unit,
                                         val job: Job?) {
        fun run(response: String, mode: SendMsgCallbackMode) {
            callback(response, mode)
            if(job != null && !job.isCancelled) job.cancel()
        }
    }

    private val mMsgCallbacks = ConcurrentHashMap<String, MsgCallbackValue>()

    override fun connect() {
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

                if (_state != 1) {
                    _state = 1
                    onConnectedListenerRef?.get()?.onConnected(this@ReconnectWebSocket, true, Reason.SUCCESS)
                }
                connectAttempts = 0
                logdNoFile { "WebSocket 连接成功" }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (webSocket !== this@ReconnectWebSocket.webSocket) return

                logdNoFile { "收到消息: $text" }
                val jo = ignoreError { JSONObject(text) }
                val msgID = jo?.optString("msgID")
                if (!msgID.isNullOrEmpty()) {
                    val value = mMsgCallbacks.remove(msgID)
                    //如果解析到了callback，则无需再解析；否则交给外部解析
                    if (value != null) {
                        value.run(text, SendMsgCallbackMode.SUCCESS)
                    } else {
                        extraParser?.onExtraParse(text, jo)
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (webSocket !== this@ReconnectWebSocket.webSocket) return

                logEx(throwable = t) { "连接失败: ${t.message}" }
                val callbacks = ArrayList(mMsgCallbacks.entries)
                mMsgCallbacks.clear()
                if (callbacks.isNotEmpty()) {
                    val msg = t.message ?: ""
                    callbacks.forEach { (_, value) ->
                        value.run(msg, SendMsgCallbackMode.FAIL)
                    }
                }
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (webSocket !== this@ReconnectWebSocket.webSocket) return

                logdNoFile { "连接关闭" }
                val callbacks = ArrayList(mMsgCallbacks.entries)
                mMsgCallbacks.clear()
                if (callbacks.isNotEmpty()) {
                    callbacks.forEach { (_, value) ->
                        value.run(reason, SendMsgCallbackMode.CLOSE)
                    }
                }
                scheduleReconnect()
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

        if (maxReconnectAttempts in 1..connectAttempts) {
            shouldReconnect = false
            connectAttempts = 0
            //不论如何都通知, 不管之前是否已经连接
            _state = 0
            onConnectedListenerRef?.get()?.onConnected(this@ReconnectWebSocket, false, Reason.MAX_ATTEMPTS_EXCEEDED)
            logdNoFile { "达到最大重连次数，停止重连" }
            return false
        } else {
            _state = -1
            onConnectedListenerRef?.get()?.onDisconnectedAndTrying(this@ReconnectWebSocket)
        }

        val currentDelay = if (maxReconnectAttempts > 0)
            quickReconnectDelay
        else
            reconnectDelays[minOf(connectAttempts, reconnectDelays.size - 1)]

        reconnectScope.launch {
            delay(currentDelay)
            if (_state == -1 && shouldReconnect) {
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
    fun disconnect(isDestroy: Boolean) {
        shouldReconnect = false

        reconnectScope.cancel()
        //   1000~1015 https://datatracker.ietf.org/doc/html/rfc6455#section-7.4
        webSocket?.close(1000, "用户手动断开")
        _state = 0
        onConnectedListenerRef?.get()?.onConnected(this@ReconnectWebSocket, false, Reason.DISCONNECT_BY_CALLER)
        if (isDestroy) {
            onDestroyed()
        }
    }

    override fun sendMsg(message: String): Boolean {
        return if (_state == 1) {
            val r = webSocket?.send(message)
            r == true
        } else {
            false
        }
    }

    override fun sendMsg(message: String, msgIDInMessage: String,
                         timeoutSecond: Int,
                         successCallback: (response: String, mode: SendMsgCallbackMode) -> Unit) : Boolean {
        return if (_state == 1) {
            val job = if (timeoutSecond > 0) {
                //这里使用全局的scope来做，不使用本地的scope，而且不会被主动cancel，哪怕断开后，也要保证有回调
                Globals.backgroundScope.launch {
                    delay(timeoutSecond * 1000L)
                    mMsgCallbacks.remove(msgIDInMessage)?.run("{}", SendMsgCallbackMode.TIMEOUT)
                }
            } else {
                null
            }
            mMsgCallbacks[msgIDInMessage] = MsgCallbackValue(successCallback, job)
            val r = webSocket?.send(message)
            r == true
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
