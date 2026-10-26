package com.allan.mydroid.client.api

import android.content.Context
import android.net.ConnectivityManager
import android.net.InetAddresses
import android.net.NetworkCapabilities
import com.au.module_android.Globals
import okhttp3.ConnectionPool
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.net.Proxy
import java.util.concurrent.TimeUnit

/** 局域网传输专用客户端，响应由调用方流式读取，业务错误由 ClientApi 解析。 */
object ClientHttp {
    val client = OkHttpClient.Builder()
        .connectTimeout(5000, TimeUnit.MILLISECONDS)
        .readTimeout(120000, TimeUnit.MILLISECONDS)
        .writeTimeout(30000, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        // NanoHTTPD 在空闲 5 秒后关闭连接，客户端提前回收以免下一次请求复用失效连接。
        .connectionPool(ConnectionPool(5, 4, TimeUnit.SECONDS))
        .build()

    fun forUrl(url: HttpUrl, retryOnConnectionFailure: Boolean = false): OkHttpClient {
        val requestClient = if (retryOnConnectionFailure) {
            client.newBuilder().retryOnConnectionFailure(true).build()
        } else client
        if (!InetAddresses.isNumericAddress(url.host)) return requestClient
        val address = InetAddresses.parseNumericAddress(url.host)
        val manager = Globals.app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val active = manager.activeNetwork
        // 手机可能把蜂窝/VPN 作为默认网络；仅把匹配局域网路由的请求绑定到 Wi-Fi。
        val network = manager.allNetworks.sortedBy { if (it == active) 0 else 1 }.firstOrNull { candidate ->
            val capabilities = manager.getNetworkCapabilities(candidate)
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true &&
                !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                manager.getLinkProperties(candidate)?.routes?.any { route ->
                    !route.isDefaultRoute && route.matches(address)
                } == true
        } ?: return requestClient
        return requestClient.newBuilder()
            .socketFactory(network.socketFactory)
            .proxy(Proxy.NO_PROXY)
            .build()
    }
}
