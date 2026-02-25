package com.au.module_okhttp.creator

import androidx.collection.LruCache
import com.au.module_android.log.logdNoFile
import okhttp3.OkHttpClient

abstract class AbsCertLanHttpApi : AbsLanApi() {
    private val clientMap = LruCache<String, OkHttpClient>(4)

    override val httpClient : OkHttpClient
        get() {
            val cert = certStr() ?: ""
            val client = clientMap[cert]
            if (client == null) {
                val builder = OkHttpClient.Builder()
                if (cert.isNotEmpty()) {
                    builder.myTrustCert(cert)
                }
                val newClient = builder.build()
                clientMap.put(cert, newClient)
                logdNoFile { "create cert client:$newClient certLen:${cert.length}" }
                return newClient
            } else {
//                logdNoFile { "get exist cert client:$client" }
                return client
            }
        }

    abstract fun certStr() : String?
}