package com.allan.mydroid.globals

import android.app.Activity
import android.os.SystemClock
import androidx.annotation.MainThread
import com.allan.mydroid.beans.wsdata.TextChatMessageBean
import com.allan.mydroid.nanohttp.MyDroidHttpServer
import com.allan.mydroid.nanohttp.WebsocketServer
import com.allan.mydroid.views.AbsLiveFragment
import com.allan.mydroid.state.GlobalReceiverFlowsObj
import com.allan.mydroid.state.GlobalServerRuntimeObj
import com.allan.mydroid.network.GlobalNetworkMonitorObj
import com.au.module_android.Globals
import com.au.module_android.init.InterestActivityCallbacks
import com.au.module_android.scopes.MainAppScope
import com.au.module_android.simpleflow.createNoStickyFlow
import com.au.module_androidui.ui.FragmentShellActivity
import com.au.module_android.utils.clearDirOldFiles
import com.au.module_android.utils.launchOnIOThread
import com.au.module_android.log.logd
import com.au.module_android.log.loge
import com.au.module_android.log.logt
import com.au.module_androidui.toast.ToastBuilder
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import java.io.IOException

class GlobalDroidServerObj(
    private val mainScope : MainAppScope
) : InterestActivityCallbacks(), KoinComponent, IDroidServerAliveTrigger {

    private val networkMonitor: GlobalNetworkMonitorObj by inject()
    private val serverRuntimeState: GlobalServerRuntimeObj by inject()
    private val receiverFlowsObj: GlobalReceiverFlowsObj by inject()

    /**
     * 服务存活停止事件流,由本类 emitAliveStopped 触发,供 AbsLiveFragment 观察。
     */
    private val _aliveStoppedFlow: MutableSharedFlow<Unit> = createNoStickyFlow()
    val aliveStoppedFlow: MutableSharedFlow<Unit> = _aliveStoppedFlow

    @Volatile
    private var httpServer: MyDroidHttpServer?= null
    @Volatile
    private var websocketServer: WebsocketServer?= null

    private var mLastHttpServerPort = 15555
    private var mLastWsServerPort = 16555

    private val aliveDeadTime = 5 * 60 * 1000L
    private val aliveTsTooFastTime = 6 * 1000L

    @Volatile private var aliveTs = SystemClock.elapsedRealtime()
    private val aliveCheckRun = Runnable {
        if (SystemClock.elapsedRealtime() - aliveTs > aliveDeadTime) {
            logd { "alive Ts timeout, stop server." }
            _aliveStoppedFlow.tryEmit(Unit)
        }
    }

    override fun updateAliveTs(from:String) {
        val cur = SystemClock.elapsedRealtime()
        if (cur - aliveTs < aliveTsTooFastTime) {
            logd { "Update alive Ts too fast ignore: $from" }
            return
        }
        aliveTs = cur
        logd { "Update alive Ts: $from" }
        Globals.mainHandler.removeCallbacks(aliveCheckRun)
        Globals.mainHandler.postDelayed(aliveCheckRun, aliveDeadTime)
    }

    private fun startServerWrap() {
        if (!serverRuntimeState.serverIsOpenFlow.value && hasLifeActivity()) {
            startServer { msg ->
                scope.launch {
                    ToastBuilder()
                        .setOnTop()
                        .setIcon("error")
                        .setMessage(msg)
                        .toast()
                }
            }
        }
    }

    @MainThread
    private fun startServer(errorCallback:(String)->Unit) {
        var retry = 0
        val maxRetry = 100
        while (retry < maxRetry) {
            val p = mLastHttpServerPort
            val wsPort = mLastWsServerPort
            logd { "start server try $retry with port: $p, wsPort: $wsPort" }
            httpServer = get<MyDroidHttpServer> { parametersOf(p) }
            websocketServer = get<WebsocketServer>{ parametersOf(wsPort) }

            try {
                httpServer?.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                try {
                    websocketServer?.start(WebsocketServer.WEBSOCKET_READ_TIMEOUT.toInt(), false)
                } catch (e: IOException) {
                    // 部分成功回滚:http 启动成功但 ws 失败,停掉 http 后整体重试
                    loge { "ws start failed, rollback http. port $wsPort ${e.message}" }
                    httpServer?.stop()
                    httpServer = null
                    websocketServer?.stop()
                    websocketServer = null
                    mLastHttpServerPort++
                    mLastWsServerPort++
                    retry++
                    continue
                }

                serverRuntimeState.setServerOpen(true)
                logt { "start server and websocket success and setPort $p to $wsPort" }
                serverRuntimeState.setPorts(p, wsPort)

                Globals.mainScope.launchOnIOThread {
                    clearDirOldFiles(nanoTempCacheChunksDir())
                    clearDirOldFiles(cacheImportCopyDir())
                }
                return
            } catch (e: IOException) {
                // http 启动失败,清理已构造实例
                loge { "http start failed port $p ${e.message}" }
                httpServer?.stop()
                httpServer = null
                websocketServer?.stop()
                websocketServer = null
                mLastHttpServerPort++
                mLastWsServerPort++
                retry++
            }
        }

        // 超过重试上限,清理并回调错误
        httpServer = null
        websocketServer = null
        errorCallback("no available port")
    }

    private fun stopServer() {
        logd { ">>>stop server." }
        httpServer?.closeAllConnections()
        websocketServer?.closeAllConnections()
        httpServer?.stop()
        websocketServer?.stop()
        httpServer = null
        websocketServer = null
        serverRuntimeState.setServerOpen(false)
        serverRuntimeState.clearPorts()
    }

    /**
     * 委托 WebsocketServer.broadcastTextChatFromApp,避免外部直接访问 websocketServer 字段。
     */
    fun broadcastTextChatFromApp(bean: TextChatMessageBean) {
        websocketServer?.broadcastTextChatFromApp(bean)
    }

    private var isObserverIpChanged = false
    private fun observerIpChanged() {
        if (isObserverIpChanged) {
            return
        }

        networkMonitor.networkFlow
            .onEach { netSt->
                when (netSt) {
                    is GlobalNetworkMonitorObj.NetworkStatus.Connected -> {
                        logd { "network status change to connected." }
                        startServerWrap()
                    }

                    GlobalNetworkMonitorObj.NetworkStatus.Disconnected,
                    GlobalNetworkMonitorObj.NetworkStatus.Uninitialized -> {
                        stopServer()
                    }
                }
            }
            .launchIn(mainScope)

        isObserverIpChanged = true
    }

    override fun onLifeOpen() {
        observerIpChanged()
        updateAliveTs("when liveOpen")
    }

    override fun onLifeOpenEach() {
        logd { "on life open each" }
        updateAliveTs("when liveOpenEach")
        startServerWrap()
    }

    override fun onLifeClose() {
        logd { "on life close." }
        stopServer()
        receiverFlowsObj.clearProgress()
        Globals.mainHandler.removeCallbacks(aliveCheckRun)
    }

    override fun isLifeActivity(activity: Activity): Boolean {
        val isActivity = activity is FragmentShellActivity
        if (!isActivity) {
            return false
        }
        val frgClass = activity.fragmentClass
        return AbsLiveFragment::class.java.isAssignableFrom(frgClass)
    }
}
