package com.allan.mydroid.globals

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.allan.mydroid.beansinner.IpInfo
import com.au.module_android.Globals
import com.au.module_android.init.IInterestLife
import com.au.module_android.utils.logd
import com.au.module_android.utils.logt
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject

class MyDroidNetworkObserver @Inject constructor() {
    private val onChanged:((ip:String?)->Unit) = { ip->
        if (ip.isNullOrEmpty()) {
            MyDroidConst.ipPortData.setValueSafe(null)
        } else {
            val liveData = MyDroidConst.ipPortData
            val v = liveData.realValue ?: IpInfo("", null, null)
            v.ip = ip
            logt { "get IpAddress set ip portData $v" }
            liveData.setValueSafe(v)
        }
    }

    private val netObserver = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            logd { "network on Available" }
            onChanged(getIpAddress())
        }
        override fun onLost(network: Network) {
            logd { "network on Lost" }
            onChanged(getIpAddress()) //华为手机，先链接上wifi，再移动网络掉线，也会回调。
        }
    }

    fun netRegister() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val manager = Globals.app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        manager.registerNetworkCallback(request, netObserver)
    }

    fun netUnregister() {
        val manager = Globals.app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        manager.unregisterNetworkCallback(netObserver)
    }
}