package com.au.module_okhttp.websocket

import com.au.module_android.log.logdNoFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 所有设备的连接管理器
 * 由于没有做锁处理，所以在多线程环境下，可能会出现问题，这里都是protected交给子类维护
 */
abstract class BaseAllConnectors {
    protected val _connectorsStateChangedFlow = MutableStateFlow<Long>(0)

    /**
     * 公开的连接状态改变Flow。这跟liveConnectListener区别是整个connectors的任一状态改变，而不是单个连接的状态改变。
     */
    val connectorsStateChangedFlow: StateFlow<Long> = _connectorsStateChangedFlow

    private fun emitConnectorsStateChanged() {
        _connectorsStateChangedFlow.tryEmit(System.currentTimeMillis())
    }

    /**
     * key 就是frameId
     */
    private val connectors = mutableMapOf<String, IWebSocket>()

    fun currentConnectedList() = connectors.values.filter { it.state == 1 }

    fun currentCerConnectedList() = connectors.values.filter { it.isCertConnect() && it.state == 1 }

    fun currentPairConnectedList() = connectors.values.filter { !it.isCertConnect() && it.state == 1 }

    fun allPairList() = connectors.values.filter { !it.isCertConnect() }

    private val liveConnectListener = object : IWebsocketConnectedListener {
        override fun onConnected(ws: IWebSocket, isConnected: Boolean, reason: IWebsocketConnectedListener.Reason) {
            if (reason == IWebsocketConnectedListener.Reason.DISCONNECT_BY_CALLER
                || reason == IWebsocketConnectedListener.Reason.MAX_ATTEMPTS_EXCEEDED) {
                //清理
                connectors.remove(ws.nameTag)
            }
            emitConnectorsStateChanged() //放出来，IWebsocket的State会在连接后回调
        }

        override fun onDisconnectedAndTrying(ws: IWebSocket) {
            logdNoFile { "连接中断，正在尝试重连..." }
            emitConnectorsStateChanged()
        }
    }

    fun getCertConnectedWebsocket(nameTag: String) : IWebSocket? {
        //1. 先检查是否已经存在连接
        val ws = connectors[nameTag]
        val isCert = ws?.isCertConnect() ?: false
        if (isCert && ws.state == 1) {
            return ws
        }
        return null
    }

    protected fun getConnectedWebsocket(nameTag: String) : IWebSocket? {
        //1. 先检查是否已经存在连接
        val ws = connectors[nameTag]
        if (ws != null && ws.state == 1) {
            return ws
        }

        return null
    }

    protected fun addConnector(ws: IWebSocket) {
        ws as ReconnectWebSocket
        connectors[ws.nameTag] = ws
        ws.setConnectedListener(liveConnectListener)
        emitConnectorsStateChanged()
    }

    protected fun disconnect(ws: IWebSocket) {
        ws as ReconnectWebSocket
        ws.disconnect(true)
        connectors.remove(ws.nameTag)
        emitConnectorsStateChanged()
    }

    protected fun disconnectAll() {
        connectors.values.forEach {
            it as ReconnectWebSocket
            it.disconnect(true)
        }
        connectors.clear()
        emitConnectorsStateChanged()
    }
}