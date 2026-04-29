package com.allan.mydroid.api

import com.allan.mydroid.beans.httpdata.IpPortResult
import com.allan.mydroid.beansinner.ShareInHtml
import com.allan.mydroid.nanohttp.WebsocketServer
import com.au.module_cached.delegate.AppDataStoreLongCache
import com.au.module_gson.fromGson
import com.au.module_okhttp.creator.AbsOkhttpApi
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

object Api : AbsOkhttpApi() {
    var timestampOffset by AppDataStoreLongCache("api_timestampOffset", 0L)

    /**
     * 当前的baseUrl
     */
    var currentBaseUrl : String = ""

    override fun getBaseUrl(): String {
        return currentBaseUrl
    }

    override fun getAppId(): String {
        return ""
    }

    override fun getAppKey(): String {
        return ""
    }

    override fun getApiToken(): String {
        return ""
    }

    override fun setupHeader(builder: Request.Builder, needToken: Boolean) {
        //无
    }

    /**
     * 读取远端HTTP与WebSocket端口信息。
     *
     * @param ip 远端IP
     * @param httpPort 远端HTTP端口
     * @return 端口信息
     * @throws Exception 网络异常或接口异常
     */
    suspend fun readWebsocketIpPort(ip: String, httpPort: Int): IpPortResult? {
        currentBaseUrl = "http://$ip:$httpPort"
        return READ_WEBSOCKET_IP_PORT.requestResultPost(needToken = false)
    }

    /**
     * 读取远端可下载文件列表。
     *
     * @param ip 远端IP
     * @param httpPort 远端HTTP端口
     * @return 文件列表
     * @throws Exception 网络异常或接口异常
     */
    suspend fun requestRemoteFileList(ip: String, httpPort: Int): List<ShareInHtml> {
        currentBaseUrl = "http://$ip:$httpPort"
        val json = REQUEST_FILE_LIST.requestResultPost<String>(needToken = false)
        return json?.fromGson<List<ShareInHtml>>() ?: emptyList()
    }

    /**
     * 创建远端文件下载地址。
     *
     * @param ip 远端IP
     * @param httpPort 远端HTTP端口
     * @param uriUuid 文件UUID
     * @return 下载地址
     */
    fun buildFileDownloadUrl(ip: String, httpPort: Int, uriUuid: String): String {
        return "http://$ip:$httpPort/file_download_uuid/$uriUuid"
    }

    // 创建并启动WebSocket连接
    fun connectWSServer(ip:String, port:Int, listener:WebSocketListener, path: String = ""): WebSocket {
        val client = OkHttpClient.Builder()
            .pingInterval(WebsocketServer.HEARTBEAT_INTERVAL, TimeUnit.MILLISECONDS)
            .build()
        val fixPath = if (path.isEmpty() || path.startsWith("/")) path else "/$path"
        val request: Request = Request.Builder().url("ws://$ip:$port$fixPath").build()
        return client.newWebSocket(request, listener)
    }
}