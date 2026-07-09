package com.au.module_okhttp.websocket

import com.au.module_android.log.logdNoFile
import com.au.module_android.utils.asOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 所有设备的连接管理器
 * 由于没有做锁处理，所以在多线程环境下，可能会出现问题，这里都是protected交给子类维护
 */
abstract class BaseAllConnectors {
    protected val mCurrentConnectorStateChangeFlow = MutableStateFlow<Long>(0)

    /**
     * 当前连接的状态改变Flow
     */
    protected val connectorsStateChangedFlow: StateFlow<Long> = mCurrentConnectorStateChangeFlow

    private fun emitConnectorsStateChanged() {
        mCurrentConnectorStateChangeFlow.tryEmit(System.currentTimeMillis())
    }

    /**
     * 当前正在使用的连接
     */
    @Volatile protected var mCurrentConnector: IWebSocket? = null

    /**
     * 获取当前connector的frameId，仅用于一些判断是否连接的状态可行。
     */
    fun getCurrentFrameConnectorFrameId() = mCurrentConnector?.nameTag

    /**
     * 获取当前connector是否已连接
     */
    fun getCurrentFrameConnectorIsConnected() = mCurrentConnector?.state == IWebSocket.State.CONNECTED

    private val currentConnectListener = object : IWebsocketConnectedListener {
        override fun onConnected(ws: IWebSocket, isConnected: Boolean, reason: IWebsocketConnectedListener.Reason) {
            if (!isConnected) {
                if (ws == mCurrentConnector) {
                    mCurrentConnector = null
                }
            }

            emitConnectorsStateChanged()
        }

        override fun onDisconnectedAndTrying(ws: IWebSocket) {
            logdNoFile { "${ws.nameTag} current连接中断，正在尝试重连..." }
            if (ws == mCurrentConnector) emitConnectorsStateChanged()
        }
    }

    protected fun setCurrentConnector(ws: IWebSocket) {
        ws as ReconnectWebSocket
        mCurrentConnector = ws
        ws.setConnectedListener(currentConnectListener)
    }

    protected fun disconnectCurrentConnector() {
        mCurrentConnector?.asOrNull<ReconnectWebSocket>()?.disconnect(true)
        mCurrentConnector = null
    }
}