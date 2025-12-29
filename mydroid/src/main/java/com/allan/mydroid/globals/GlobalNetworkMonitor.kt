package com.allan.mydroid.globals

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.allan.mydroid.R
import com.au.module_android.Globals
import com.au.module_android.Globals.resStr
import com.au.module_android.init.GlobalBackgroundCallback
import com.au.module_android.scopes.BackAppScope
import com.au.module_android.simpleflow.StatusState
import com.au.module_android.utils.getIpAddress
import com.au.module_android.utils.logd
import com.au.module_android.utils.logdNoFile
import com.au.module_android.utils.logt
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class GlobalNetworkMonitor(
    private val droidServer: GlobalDroidServer,
    private val backScope : BackAppScope,
) {
    val networkFlow: Flow<NetworkStatus> = callbackFlow {
        fun getIpAndTrySend() {
            val (ip, netType) = getIpAddress()
            val st = if (ip == null) {
                NetworkStatus.Disconnected
            } else {
                NetworkStatus.Connected(ip, netType.toString())
            }
            trySend(st)
        }

        val netObserver = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                logd { "network on Available" }
                getIpAndTrySend()
            }

            override fun onLost(network: Network) {
                logd { "network on Lost" }
                getIpAndTrySend()
            }
        }

        GlobalBackgroundCallback.addListener(object : GlobalBackgroundCallback.IBackgroundListener {
            override fun onBackground(isBackground: Boolean) {
                logdNoFile { "Network observer is in bg $isBackground" }
                val manager = Globals.app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                if (isBackground) {
                    logd { "Unregistering network callback" }
                    manager.unregisterNetworkCallback(netObserver)
                } else {
                    // 注册网络回调
                    logd { "Starting network" }
                    val request = NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build()
                    manager.registerNetworkCallback(request, netObserver)
                }
            }
        })

        getIpAndTrySend()

        // 当 Flow 被取消时清理资源
        awaitClose {

        }
    }

    val networkInfoFlow = networkFlow.combine(droidServer.portsFlow) { networkStatus, statusState->
        logt { "networkInfo combine $networkStatus $statusState" }
        when (networkStatus) {
            is NetworkStatus.Connected -> {
                val strFmt = R.string.server_m.resStr()
                val ports = if(statusState is StatusState.Success<Pair<Int, Int>>) statusState.data else null
                val httpPort = ports?.first
                val wsPort = ports?.second
                val ip = networkStatus.ip

                NetworkInfo(ip, httpPort, wsPort, networkStatus.networkType,
                    String.format(strFmt, "${ip}:${httpPort}"))
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