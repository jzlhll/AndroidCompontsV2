package com.allan.mydroid.globals

import android.app.Activity
import android.os.SystemClock
import androidx.annotation.MainThread
import com.allan.mydroid.nanohttp.MyDroidHttpServer
import com.allan.mydroid.nanohttp.WebsocketServer
import com.allan.mydroid.views.AbsLiveFragment
import com.au.module_android.Globals
import com.au.module_android.init.InterestActivityCallbacks
import com.au.module_android.scopes.MainAppScope
import com.au.module_android.simpleflow.createStatusStateFlow
import com.au.module_android.simpleflow.setSuccess
import com.au.module_androidui.ui.FragmentShellActivity
import com.au.module_android.utils.clearDirOldFiles
import com.au.module_android.utils.launchOnIOThread
import com.au.module_android.log.logd
import com.au.module_android.log.loge
import com.au.module_android.log.logt
import com.au.module_androidui.toast.ToastBuilder
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import java.io.IOException
import java.net.ServerSocket

class GlobalDroidServer(
    private val mainScope : MainAppScope
) : InterestActivityCallbacks(), KoinComponent, IDroidServerAliveTrigger {

    private val networkMonitor: GlobalNetworkMonitor by inject()

    private var httpServer: MyDroidHttpServer?= null
    var websocketServer: WebsocketServer?= null

    private var mLastHttpServerPort = 15555
    private var mLastWsServerPort = 16555

    private val aliveDeadTime = 5 * 60 * 1000L //N分钟不活跃主动关闭服务
    private val aliveTsTooFastTime = 6 * 1000L //n秒内的更新，只干一次就好。很严谨来讲需要考虑再次post，但是由于相去很远忽略这几秒。

    /**
     * 端口信息：左值httpPort，右值websocketPort
     * 状态容器（重放最新状态）
     */
    val portsFlow = createStatusStateFlow<Pair<Int, Int>>()

    /**
     * 如果很久没有从html端请求接口，则主动关闭服务
     */
    private var aliveTs = SystemClock.elapsedRealtime()
    private val aliveCheckRun = Runnable {
        if (SystemClock.elapsedRealtime() - aliveTs > aliveDeadTime) {
            logd { "alive Ts timeout, stop server." }
            MyDroidConst.aliveStoppedData.setValueSafe(Unit)
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

    private fun findAvailablePort(): Int {
        while (mLastHttpServerPort < 65535) {
            try {
                ServerSocket(mLastHttpServerPort).close()
                return mLastHttpServerPort
            } catch (_: IOException) { mLastHttpServerPort++ }
        }
        return -1
    }

    private fun findAvailableWsPort(): Int {
        while (mLastWsServerPort < 65535) {
            try {
                ServerSocket(mLastWsServerPort).close()
                return mLastWsServerPort
            } catch (_: IOException) { mLastWsServerPort++ }
        }
        return -1
    }

    private fun startServerWrap() {
        if (!MyDroidConst.serverIsOpen && hasLifeActivity()) {
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
        val p = findAvailablePort()
        val wsPort = findAvailableWsPort()
        logd { "start server with port: $p, wsPort: $wsPort" }
        httpServer = get<MyDroidHttpServer> { parametersOf(p) }
        websocketServer = get<WebsocketServer>{ parametersOf(wsPort) }

        try {
            httpServer?.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            websocketServer?.start(WebsocketServer.WEBSOCKET_READ_TIMEOUT.toInt(), false)

            MyDroidConst.serverIsOpen = true
            logt { "start server and websocket success and setPort $p to $wsPort" }
            portsFlow.setSuccess( p to wsPort)

            //检查并清理过期temp文件
            Globals.mainScope.launchOnIOThread {
                clearDirOldFiles(nanoTempCacheChunksDir())
                clearDirOldFiles(cacheImportCopyDir())
            }
        } catch (e: IOException) {
            val msg = "Port $p WsPort $wsPort occupied ${e.message}"
            loge { msg }
            errorCallback(msg)
        }
    }

    private fun stopServer() {
        logd { ">>>stop server." }
        httpServer?.closeAllConnections()
        websocketServer?.closeAllConnections()
        MyDroidConst.serverIsOpen = false
    }

    //////////////////////////life////
    private var isObserverIpChanged = false
    private fun observerIpChanged() {
        if (isObserverIpChanged) {
            return
        }

        networkMonitor.networkFlow
            .onEach { netSt->
                when (netSt) {
                    is GlobalNetworkMonitor.NetworkStatus.Connected -> {
                        logd { "network status change to connected." }
                        startServerWrap()
                    }

                    GlobalNetworkMonitor.NetworkStatus.Disconnected,
                    GlobalNetworkMonitor.NetworkStatus.Uninitialized -> {
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
        MyDroidConst.receiverProgressData.setValueSafe(emptyMap())
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