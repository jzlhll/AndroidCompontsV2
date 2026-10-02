package com.allan.mydroid.client.api

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** 局域网传输专用客户端，响应由调用方流式读取，业务错误由 ClientApi 解析。 */
object ClientHttp {
    val client = OkHttpClient.Builder()
        .connectTimeout(5000, TimeUnit.MILLISECONDS)
        .readTimeout(120000, TimeUnit.MILLISECONDS)
        .writeTimeout(30000, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .build()
}
