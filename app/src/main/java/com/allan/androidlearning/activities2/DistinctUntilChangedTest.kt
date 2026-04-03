package com.allan.androidlearning.activities2

/**
 * 研究用：combine(currentFrameFlow, connectorsStateChangedFlow) 与可变 WebSocket 列表。
 * 可复制到带 kotlinx-coroutines-core 的 JVM 工程运行 main；本仓库根目录默认不参与 Gradle 编译。
 */

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random

/** 模拟连接器：nameTag 唯一标识，state 仅 0 / 1 */
class Websocket(val nameTag: String, var state: Int) {
    private val myAddr = UUID.randomUUID().toString().take(8)

    override fun toString(): String {
        return "Websocket('$myAddr': nameTag='$nameTag', state=$state)"
    }
}

/**
 * 全局研究用状态：列表 + 两路 StateFlow。
 * 三个随机操作由你在调试器或自行代码中调用，每次调用都会推进 [mConnectorsStateChangedFlow]。
 */
object WebsocketFlowResearch {
    private val listLock = Any()

    /** 当前连接器列表（模拟 BaseAllConnectors 等处的 List） */
    val websocketList = CopyOnWriteArrayList<Websocket>()

    private val mConnectorsStateChangedFlow = MutableStateFlow(0L)
    val connectorsStateChangedFlow: StateFlow<Long> = mConnectorsStateChangedFlow

    private val mCurrentFrameFlow = MutableStateFlow(
        "${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}"
    )
    val currentFrameFlow: StateFlow<String> = mCurrentFrameFlow

    /** 推进 connectors 版本号，触发 combine */
    private fun notifyConnectorsChanged() {
        mConnectorsStateChangedFlow.value = System.currentTimeMillis()
    }

    fun tickCurrentFrameFromTime() {
        val s = "${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}"
        mCurrentFrameFlow.value = s
    }

    /** 随机追加一个 Websocket（nameTag 随机，state 为 0 或 1） */
    fun randomAppendWebsocket() {
        val tag = UUID.randomUUID().toString().take(8)
        val st = Random.nextInt(0, 2)
        synchronized(listLock) {
            websocketList.add(Websocket(tag, st))
        }
        notifyConnectorsChanged()
    }

    /** 随机删除一个 Websocket；列表为空则无操作 */
    fun randomRemoveWebsocket() {
        synchronized(listLock) {
            if (websocketList.isEmpty()) return@synchronized
            val idx = Random.nextInt(websocketList.size)
            websocketList.removeAt(idx)
        }
        notifyConnectorsChanged()
    }

    /** 随机选一个 Websocket，将 state 在 0 / 1 间切换 */
    fun randomToggleWebsocketState() {
        synchronized(listLock) {
            if (websocketList.isEmpty()) return@synchronized
            val idx = Random.nextInt(websocketList.size)
            val old = websocketList[idx]
            old.state = if (old.state == 0) 1 else 0
        }
        notifyConnectorsChanged()
    }
}
