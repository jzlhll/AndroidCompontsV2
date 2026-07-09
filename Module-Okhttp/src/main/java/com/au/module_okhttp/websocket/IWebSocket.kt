package com.au.module_okhttp.websocket

import androidx.annotation.WorkerThread
import org.json.JSONObject

/**
 * WebSocket接口定义
 * 之所以没有disconnect，交给AllManager去管理
 */
interface IWebSocket {
    /**
     * 框架会使用这个nameTag来标识不同的连接。具体到每个项目可能是ip，可能是某个id。
     */
    val nameTag: String

    /**
     * 重新创建连接，会重新发起连接
     */
    @WorkerThread
    fun connect(onceStateConnected:((IWebSocket)->Unit)?)

    /**
     * 发送消息
     * 返回值只是一个回执，并不代表对面收到了
     */
    @WorkerThread
    fun sendMsg(message: String): Boolean

    /**
     * 发送消息
     * 返回值只是一个回执，并不代表对面收到了
     * callback将会回调你相同的msgIDInMessage的消息内容
     *
     * @param successCallback text/jo是原始数据和他的JSONObject
     */
    fun sendMsg(message:String, msgIDInMessage:String,
                timeoutTs: Long = 0,
                successCallback:(text:String, jo:JSONObject, mode:SendMsgCallbackMode)->Unit) : Boolean

    /** 当前连接状态 */
    val state: State

    val ip: String

    fun isCertConnect() : Boolean {
        return this is CertReconnectWebSocket
    }

    class Builder {
        var ip: String = ""
        var port: Int = -1
        var headers: Map<String, String> = emptyMap()
        var certStr: String? = null
        var maxReconnectAttempts: Int = 3

        var target:String = "/"
//        var listener: IWebsocketConnectedListener? = null

        private var nameTag: String = ""

        var extraParser: IExtraParser = object : IExtraParser {
            override fun onExtraParse(nameTag: String, text: String, jo: JSONObject) {
                // 默认空实现
            }
        }

        fun ip(value: String, port:Int, target:String) = apply {
            this.ip = value
            this.port = port
            this.target = target
        }

        fun headers(value: Map<String, String>) = apply {
            this.headers = value
        }

        fun cert(certStr: String) = apply {
            this.certStr = certStr
        }

        fun maxReconnectAttempts(value: Int) = apply {
            this.maxReconnectAttempts = value
        }

        fun extraParser(parser: IExtraParser) = apply {
            this.extraParser = parser
        }

//        fun listener(listener: IWebsocketConnectedListener?) = apply {
//            this.listener = listener
//        }

        fun nameTag(nameTag: String) = apply {
            this.nameTag = nameTag
        }

        fun build() : IWebSocket {
            val cf = certStr
            if (ip.isEmpty() || port <= 0) {
                throw IllegalArgumentException("ip or port is empty")
            }

            val wsUrl = "wss://$ip:$port$target"
            val tag = nameTag.ifEmpty { "$ip:$port" }

            return if (cf == null) {
                ReconnectWebSocket(tag, ip, wsUrl, headers, maxReconnectAttempts)
            } else {
                CertReconnectWebSocket(tag, ip, wsUrl, cf, headers, maxReconnectAttempts)
            }.also {
                it.extraParser = extraParser
//                it.setConnectedListener(listener)
            }
        }
    }

    enum class State {
        DISCONNECTED,
        CONNECTED,
        RECONNECTING
    }
}

interface IWebSocketClose {
    fun disconnect(isDestroy: Boolean)
}