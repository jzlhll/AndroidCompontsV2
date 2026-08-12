package com.allan.mydroid.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.allan.mydroid.R
import com.allan.mydroid.state.GlobalServerRuntimeObj
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

class GlobalNetworkMonitorObj(
    private val serverRuntimeState: GlobalServerRuntimeObj,
    private val backScope : BackAppScope,
) {
    private val networkEventFlow = callbackFlow<Unit> {

        val manager = Globals.app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        var isRegister = false

        val netObserver = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                logd { "network on Available" }
                trySend(Unit)
            }

            override fun onLost(network: Network) {
                logd { "network on Lost" }
                trySend(Unit)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                logd { "network on CapabilitiesChanged" }
                trySend(Unit)
            }
        }

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

        trySend(Unit)

        awaitClose {
            GlobalBackgroundCallback.removeListener(bgListener)
            if (isRegister) {
                manager.unregisterNetworkCallback(netObserver)
                isRegister = false
            }
        }
    }

    val networkFlow: Flow<NetworkStatus> = networkEventFlow
        .map {
            val (ip, netType) = getIpAddress()
            if (ip == null) {
                NetworkStatus.Disconnected
            } else {
                NetworkStatus.Connected(ip, netType.toString())
            }
        }
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
