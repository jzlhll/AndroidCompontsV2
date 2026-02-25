package com.au.module_okhttp.websocket

interface IWebsocketConnectedListener {
    enum class Reason {
        SUCCESS,
        MAX_ATTEMPTS_EXCEEDED,
        DISCONNECT_BY_CALLER,
    }

    fun onConnected(ws: IWebSocket, isConnected: Boolean, reason: Reason)

    /**
     * 发生了中断，正在尝试连接中...
     */
    fun onDisconnectedAndTrying(ws: IWebSocket)
}