package com.au.module_okhttp.websocket

import com.au.module_okhttp.creator.myTrustCert
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

internal class CertReconnectWebSocket(
    override val nameTag: String,
    ip:String,
    url: String,
    private val certStr: String,
    headers: Map<String, String> = emptyMap(),
    maxReconnectAttempts: Int) : ReconnectWebSocket(nameTag, ip, url, headers, maxReconnectAttempts) {

    override fun toString(): String {
        return "Cert" + super.toString()
    }

    override fun createOkHttpClient(): OkHttpClient {
        // 创建OkHttpClient
        val client = OkHttpClient.Builder()
            .myTrustCert(certStr)
            .pingInterval(30, TimeUnit.SECONDS) // 保持心跳
            .build()
        return client
    }


}
