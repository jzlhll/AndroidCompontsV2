package com.allan.mydroid.nanohttp.handlers

import com.allan.mydroid.api.READ_WEBSOCKET_IP_PORT
import com.allan.mydroid.api.REQUEST_FILE_LIST
import com.allan.mydroid.beans.httpdata.IpPortResult
import com.allan.mydroid.nanohttp.CODE_SUC
import com.allan.mydroid.nanohttp.okJsonResponse
import com.allan.mydroid.network.GlobalNetworkMonitorObj
import com.allan.mydroid.repository.GlobalShareInRepoObj
import com.au.module_android.Globals
import com.au.module_android.log.logdNoFile
import com.au.module_gson.toGsonString
import com.au.module_okhttp.api.ResultBean
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response
import kotlinx.coroutines.runBlocking
import org.koin.core.component.inject

/**
 * 处理 GET/POST 的指令类请求：读取 websocket ip/port、请求文件列表。
 */
class CommandRequestHandler(
    private val globalNetworkMonitor: GlobalNetworkMonitorObj,
) : AbsHttpRequestHandler() {

    private val shareInRepository: GlobalShareInRepoObj by inject()

    override fun tryHandle(method: NanoHTTPD.Method, uri: String, session: IHTTPSession): Response? {
        when (method) {
            NanoHTTPD.Method.GET -> {
                if (uri == READ_WEBSOCKET_IP_PORT) {
                    return getWebsocketIpPort()
                }
            }
            NanoHTTPD.Method.POST -> {
                when (uri) {
                    READ_WEBSOCKET_IP_PORT -> return getWebsocketIpPort()
                    REQUEST_FILE_LIST -> return getFileList()
                }
            }
            else -> {}
        }
        return null
    }

    private fun getFileList(): Response {
        return runBlocking {
            val beans = shareInRepository.loadShareInAndReceiveBeans()
            val json = beans.toGsonString()
            if (json.isNotEmpty()) {
                ResultBean(CODE_SUC, "Success!", json).okJsonResponse()
            } else {
                invalidRequestResponse()
            }
        }
    }

    private fun getWebsocketIpPort(): Response {
        val data = globalNetworkMonitor.networkInfoFlow.value
        logdNoFile { "get websocket ip port $data" }
        if (data == null) {
            return newNotFoundResponse(Globals.getString(com.allan.mydroid.R.string.invalid_request_from_appserver))
        }

        val ip = data.ip
        val wsPort = data.wsPort
        val httpPort = data.httpPort

        return if (wsPort != null && httpPort != null) {
            val info = IpPortResult(ip ?: "", wsPort, httpPort)
            logdNoFile { "get websocket ipPort $info" }
            ResultBean(CODE_SUC, "Success!", info).okJsonResponse()
        } else {
            invalidRequestResponse()
        }
    }
}
