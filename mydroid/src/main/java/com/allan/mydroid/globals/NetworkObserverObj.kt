package com.allan.mydroid.globals

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.allan.mydroid.beansinner.IpInfo
import com.au.module_android.Globals
import com.au.module_android.Globals.resStr
import com.au.module_android.init.GlobalBackgroundCallback
import com.au.module_android.utils.logd
import com.au.module_android.utils.logdNoFile

object NetworkObserverObj {
    fun getServerName() : String? {
        val st = MyDroidConst.networkStatusData.value!!
        return when (st) {
            is NetworkStatus.Connected -> {
                val strFmt = com.allan.mydroid.R.string.server_m.resStr()
                String.format(strFmt, "${st.ipInfo.ip}:${st.ipInfo.httpPort}")
            }

            is NetworkStatus.Uninitialized,
            is NetworkStatus.Disconnected -> {
                null
            }
        }
    }

    private val getIpAndTrySend = {
        val (ip, netType) = getIpAddress()
        if (ip == null) {
            MyDroidConst.networkStatusData.setValueSafe(NetworkStatus.Disconnected)
        } else {
            MyDroidConst.networkStatusData.setValueSafe(NetworkStatus.Connected(IpInfo(ip, null, null), netType!!))
        }
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

    fun initial() {
        getIpAndTrySend()

        GlobalBackgroundCallback.addListener { inBg->
            logdNoFile { "Network observer is in bg $inBg" }
            val manager = Globals.app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (inBg) {
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
    }

//    private val networkStatusFlow: Flow<NetworkStatus> = callbackFlow {
//
//
//        getIpAndTrySend()
//
//        // 当 Flow 被取消时清理资源
//        awaitClose {
//
//        }
//    }

//    val serverNameFlow : Flow<String?> = networkStatusFlow.map {
//        val strFmt = com.allan.mydroid.R.string.server_m.resStr()
//        when (it) {
//            is NetworkStatus.Uninitialized,
//            is NetworkStatus.Disconnected -> {
//                null
//            }
//            is NetworkStatus.Connected -> {
//                String.format(strFmt, "${it.ipInfo.ip}:${it.ipInfo.httpPort}")
//            }
//        }
//    }

    // 定义更丰富的网络状态
    sealed class NetworkStatus {
        object Uninitialized : NetworkStatus()
        object Disconnected : NetworkStatus()
        data class Connected(val ipInfo: IpInfo, val networkType: String) : NetworkStatus()
    }
}