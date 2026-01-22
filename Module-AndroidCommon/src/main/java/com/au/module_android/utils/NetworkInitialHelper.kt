package com.au.module_android.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.au.module_android.Globals
import com.au.module_android.init.GlobalBackgroundCallback
import com.au.module_android.log.logdNoFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 需要监听网络变化，必须干成一件事。
 * @param work 必须完成的任务，返回是否成功。成功了就标记mIsInitial为true。
 */
class NetworkInitialHelper(
    val tag : String = "NetworkInitial",
    val scope : CoroutineScope,
    val work: suspend () -> Boolean
) : ConnectivityManager.NetworkCallback() {
    private var mIsInitial = false //是否完成了任务呢。
    fun isInitial() : Boolean {
        return mIsInitial
    }

    /**
     * 执行任务。如果失败了，就注册网络回调，再执行。完成后就自己取消注册。
     */
    fun doInitialWork() {
        scope.launch {
            mIsInitial = work.invoke()
            if (!mIsInitial) {
                registerNetworkOb()
            }
        }
    }

    override fun onAvailable(network: Network) {
        if (GlobalBackgroundCallback.isForeground && !mIsInitial) {
            scope.launch {
                var count = 0
                do {
                    delay(500L + 2500L * count)
                    mIsInitial = work.invoke()
                    if (mIsInitial) {
                        break
                    }
                } while(++count <= 2)
                if (mIsInitial) {
                    unregisterNetworkOb()
                }
            }
        }
    }

// 检查网络是否通过验证（能访问外网）
//    private fun isNetworkValidated(network: Network): Boolean {
//        val connectivityManager = Globals.app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
//        val capabilities = connectivityManager.getNetworkCapabilities(network)
//        return capabilities != null &&
//                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
//                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
//    }

    private var mIsRegisterNetOb = false
    private fun unregisterNetworkOb() {
        if (mIsRegisterNetOb) {
            mIsRegisterNetOb = false
            val manager = Globals.app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            manager.unregisterNetworkCallback(this)
            logdNoFile {"$tag unregister network callback"}
        }
    }

    private fun registerNetworkOb() {
        if (!mIsRegisterNetOb) {
            val manager = Globals.app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            mIsRegisterNetOb = true
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .build()
            manager.registerNetworkCallback(request, this)
            logdNoFile {"$tag register network callback"}
        }
    }
}