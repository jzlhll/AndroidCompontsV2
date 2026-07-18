package com.allan.mydroid.globals

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.allan.mydroid.R
import com.au.module_android.Globals
import com.au.module_android.init.GlobalBackgroundCallback
import com.au.module_android.scopes.BackAppScope
import com.au.module_android.simpleflow.StatusState
import com.au.module_android.utils.getIpAddress
import com.au.module_android.log.logd
import com.au.module_android.log.logdNoFile
import com.au.module_android.log.logt
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Duration.Companion.seconds

class GlobalNetworkMonitor(
    private val droidServer: GlobalDroidServer,
    private val backScope : BackAppScope,
) {
    // 第一层：只收集"网络变化事件"，不获取 IP。所有 onXXX 回调统一 trySend(Unit)
    private val networkEventFlow = callbackFlow<Unit> {

        // 统一在 block 顶层声明，bgListener 注册/注销与 awaitClose 清理共用
        val manager = Globals.app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        var isRegister = false  // 改为 block 局部变量，消除多实例共享竞态

        val netObserver = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                logd { "network on Available" }
                trySend(Unit)
            }

            override fun onLost(network: Network) {
                logd { "network on Lost" }
                trySend(Unit)
            }

            // 新增：覆盖 IP 分配完成、默认网络切换、validated 变化等场景
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                logd { "network on CapabilitiesChanged" }
                trySend(Unit)
            }
        }

        // 提取为具名局部变量，便于 awaitClose 移除
        val bgListener = object : GlobalBackgroundCallback.IBackgroundListener {
            override fun onBackground(isBackground: Boolean) {
                logdNoFile { "Network observer is in bg $isBackground" }
                if (isBackground) {
                    logd { "Unregistering network callback" }
                    if (isRegister) {
                        manager.unregisterNetworkCallback(netObserver)
                        isRegister = false
                    }
                } else {
                    logd { "Starting network" }
                    if (!isRegister) {
                        manager.registerNetworkCallback(request, netObserver)
                        isRegister = true
                    }
                }
            }
        }

        GlobalBackgroundCallback.addListener(bgListener)

        trySend(Unit)  // 初始触发一次，走防抖调度获取当前 IP

        // 清理资源：移除 listener、注销 NetworkCallback
        awaitClose {
            GlobalBackgroundCallback.removeListener(bgListener)
            if (isRegister) {
                manager.unregisterNetworkCallback(netObserver)
                isRegister = false
            }
        }
    }

    // 第二层：防抖 2 秒 + 延迟获取 IP
    // debounce(2.seconds) 同时实现两个目的：
    //   1. 防抖：连续 onXXX 回调合并为一次获取
    //   2. 延迟：最后一次回调后等 2 秒，确保 DHCP 完成，getIpAddress() 能读到真实 IP
    //
    // stateIn scope 用 Globals.mainScope（Dispatchers.Main.immediate）而非 backScope：
    //   保证 callbackFlow block（含 addListener）在首个订阅者 collect 时同步执行，
    //   冷启动时 addListener 在 onActivityCreated 中完成，早于 onActivityStarted 的 onBackground(false)，
    //   bgListener 能收到 onBackground(false) 进而注册 NetworkCallback。
    val networkFlow: Flow<NetworkStatus> = networkEventFlow
        .debounce(2.seconds)
        .map {
            val (ip, netType) = getIpAddress()
            if (ip == null) {
                NetworkStatus.Disconnected
            } else {
                NetworkStatus.Connected(ip, netType.toString())
            }
        }
        .stateIn(scope = Globals.mainScope, started = SharingStarted.Lazily, initialValue = NetworkStatus.Uninitialized)

    val networkInfoFlow = networkFlow.combine(droidServer.portsFlow) { networkStatus, statusState->
        logt { "networkInfo combine $networkStatus $statusState" }
        when (networkStatus) {
            is NetworkStatus.Connected -> {
                val strFmt = Globals.getString(R.string.server_m)
                val ports = if(statusState is StatusState.Success<Pair<Int, Int>>) statusState.data else null
                val httpPort = ports?.first
                val wsPort = ports?.second
                val ip = networkStatus.ip

                NetworkInfo(
                    ip, httpPort, wsPort, networkStatus.networkType,
                    String.format(strFmt, "${ip}:${httpPort}")
                )
            }

            is NetworkStatus.Uninitialized,
            is NetworkStatus.Disconnected -> {
                null
            }
        }
    }.stateIn(
        scope = backScope,
        started = SharingStarted.Lazily,
        initialValue = null
    )

    // 定义更丰富的网络状态
    sealed class NetworkStatus {
        object Uninitialized : NetworkStatus()
        object Disconnected : NetworkStatus()
        data class Connected(val ip: String, val networkType: String) : NetworkStatus()
    }

    data class NetworkInfo(val ip: String?,
                           val httpPort:Int?, val wsPort:Int?,
                           val networkType: String,
                           val ipHttpPortStr: String)
}
