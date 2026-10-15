package com.allan.mydroid.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.allan.mydroid.R
import com.allan.mydroid.state.GlobalServerRuntimeObj
import com.au.module_android.Globals
import com.au.module_android.init.GlobalBackgroundCallback
import com.au.module_android.log.logt
import com.au.module_android.scopes.BackAppScope
import com.au.module_android.simpleflow.StatusState
import com.au.module_android.utils.getIpAddress
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 持续跟踪 Wi-Fi 和热点地址，统一提供页面与服务端的连接信息。 */
class GlobalNetworkMonitorObj(
    private val serverRuntimeState: GlobalServerRuntimeObj,
    private val backScope : BackAppScope,
) {
    private val networkEventFlow = callbackFlow<Unit> {
        val manager = Globals.app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        // 局域网传输不要求外网能力，且需要观察非默认 Wi-Fi 网络。
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        val netObserver = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(Unit) }
            override fun onLost(network: Network) { trySend(Unit) }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(Unit)
            }
            override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) {
                trySend(Unit)
            }
        }
        val bgListener = object : GlobalBackgroundCallback.IBackgroundListener {
            override fun onBackground(isBackground: Boolean) {
                if (!isBackground) trySend(Unit)
            }
        }
        // 注册时立即读取现状，不能等待已经发生过的前台事件。
        manager.registerNetworkCallback(request, netObserver)
        GlobalBackgroundCallback.addListener(bgListener)
        trySend(Unit)
        // 热点下行没有 NetworkCallback；前台和服务存活期间补充刷新。
        val refreshJob = launch {
            while (isActive) {
                delay(2000.milliseconds)
                if (GlobalBackgroundCallback.isForeground || serverRuntimeState.serverIsOpenFlow.value) {
                    trySend(Unit)
                }
            }
        }
        awaitClose {
            refreshJob.cancel()
            GlobalBackgroundCallback.removeListener(bgListener)
            manager.unregisterNetworkCallback(netObserver)
        }
    }.buffer(Channel.CONFLATED)

    val networkFlow: StateFlow<NetworkStatus> = networkEventFlow
        .map {
            val (ip, netType) = getIpAddress()
            if (ip == null) {
                NetworkStatus.Disconnected
            } else {
                NetworkStatus.Connected(ip, netType.toString())
            }
        }
        .flowOn(Dispatchers.IO)
        .distinctUntilChanged()
        .stateIn(scope = Globals.mainScope, started = SharingStarted.Lazily, initialValue = NetworkStatus.Uninitialized)

    val networkInfoFlow = networkFlow.combine(serverRuntimeState.portsFlow) { networkStatus, statusState->
        logt { "networkInfo combine $networkStatus $statusState" }
        when (networkStatus) {
            is NetworkStatus.Connected -> {
                val strFmt = Globals.getString(R.string.server_m)
                val ports = if(statusState is StatusState.Success<Pair<Int, Int>>) statusState.data else null
                val httpPort = ports?.first
                val wsPort = ports?.second
                val ip = networkStatus.ip
                val host = if (ip.contains(':')) "[$ip]" else ip

                NetworkInfo(
                    ip, httpPort, wsPort, networkStatus.networkType,
                    String.format(strFmt, if (httpPort == null) ip else "$host:$httpPort")
                )
            }

            is NetworkStatus.Uninitialized,
            is NetworkStatus.Disconnected -> {
                null
            }
        }
    }.stateIn(
        scope = backScope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    sealed class NetworkStatus {
        object Uninitialized : NetworkStatus()
        object Disconnected : NetworkStatus()
        data class Connected(val ip: String, val networkType: String) : NetworkStatus()
    }

    data class NetworkInfo(val ip: String?,
                           val httpPort:Int?, val wsPort:Int?,
                           val networkType: String,
                           val ipHttpPortStr: String) {
        val urlHost: String? get() = ip?.let { if (it.contains(':')) "[$it]" else it }
    }
}
