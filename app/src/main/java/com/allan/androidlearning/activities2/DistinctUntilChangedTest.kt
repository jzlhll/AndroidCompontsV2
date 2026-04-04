package com.allan.androidlearning.activities2

/**
 * 研究用：combine(currentFrameFlow, connectorsStateChangedFlow) 与可变 WebSocket 列表。
 * 可复制到带 kotlinx-coroutines-core 的 JVM 工程运行 main；本仓库根目录默认不参与 Gradle 编译。
 */

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random

enum class DeviceState {
    Connected,
    Connecting,
    Disconnected,
    Disconnecting
}

/** 模拟连接器：deviceId 唯一标识 */
class Device(val deviceId: String, var state: DeviceState) {
    private val beanAddr = UUID.randomUUID().toString().take(8)

    override fun toString(): String {
        return "Device('$beanAddr': deviceId='$deviceId', state=$state)"
    }
}

/**
 * 全局研究用状态：列表 + 两路 StateFlow。
 * 三个随机操作由你在调试器或自行代码中调用，每次调用都会推进 [mConnectorsStateChangedFlow]。
 */
object WebsocketFlowResearch {
    private val listLock = Any()
    private var websocketIndex = 1

    /** 当前连接器列表（模拟 BaseAllConnectors 等处的 List） */
    val websocketList = CopyOnWriteArrayList<Device>()

    private val mConnectorsStateChangedFlow = MutableStateFlow(0L)
    val connectorsStateChangedFlow: StateFlow<Long> = mConnectorsStateChangedFlow

    private val mCurrentFrameFlow = MutableStateFlow(
        "${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}"
    )
    val currentFrameFlow: StateFlow<String> = mCurrentFrameFlow

    val deviceStateFlow = combine(
        currentFrameFlow,
        connectorsStateChangedFlow
    ) { frame, changed ->
        android.util.Log.d("alland", "frame $frame change $changed\n"
                + "Details: \n---\n${websocketList.joinToString("\n")}\n")
        websocketList.find { it.deviceId == frame }
    }.distinctUntilChangedBy {
        it?.deviceId + it?.state.toString()
    }
    
    //.distinctUntilChanged()



    /** 推进 connectors 版本号，触发 combine */
    private fun notifyConnectorsChanged() {
        mConnectorsStateChangedFlow.value = System.currentTimeMillis()
    }

    fun tickCurrentFrameFromTime() {
        synchronized(listLock) {
            if (websocketList.isNotEmpty()) {
                val idx = Random.nextInt(websocketList.size)
                val selectedNameTag = websocketList[idx].deviceId
                mCurrentFrameFlow.value = selectedNameTag
            }
        }
    }

    /** 随机追加一个 Device（deviceId 递增，state 随机） */
    fun randomAppendWebsocket() {
        val st = DeviceState.entries.random()
        synchronized(listLock) {
            val tag = String.format("websocket-%02d", websocketIndex++)
            websocketList.add(Device(tag, st))
        }
        notifyConnectorsChanged()
    }

    /** 随机删除一个 Device；列表为空则无操作 */
    fun randomRemoveWebsocket() {
        synchronized(listLock) {
            if (websocketList.isEmpty()) return@synchronized
            val idx = Random.nextInt(websocketList.size)
            websocketList.removeAt(idx)
        }
        notifyConnectorsChanged()
    }

    /** 随机选一个 Device，将 state 按逻辑流转推进下一个状态 */
    fun randomAdvanceWebsocketState() {
        synchronized(listLock) {
            if (websocketList.isEmpty()) return@synchronized
            val idx = Random.nextInt(websocketList.size)
            val old = websocketList[idx]
            Log.d("alland", "change ${old.deviceId}...")
            old.state = when (old.state) {
                DeviceState.Disconnected -> DeviceState.Connecting
                DeviceState.Connecting -> DeviceState.Connected
                DeviceState.Connected -> DeviceState.Disconnecting
                DeviceState.Disconnecting -> DeviceState.Disconnected
            }
        }
        notifyConnectorsChanged()
    }
}