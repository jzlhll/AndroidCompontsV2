package com.allan.mydroid.nanohttp

import com.allan.mydroid.globals.IDroidServerAliveTrigger
import com.allan.mydroid.nanohttp.handlers.AbsHttpRequestHandler
import com.allan.mydroid.nanohttp.handlers.ChunkUploadHandler
import com.allan.mydroid.nanohttp.handlers.CommandRequestHandler
import com.allan.mydroid.nanohttp.handlers.FileDownloadHandler
import com.allan.mydroid.nanohttp.handlers.StaticAssetHandler
import com.allan.mydroid.network.GlobalNetworkMonitorObj
import com.allan.mydroid.state.GlobalReceiverFlowsObj
import com.au.module_android.Globals
import com.au.module_android.log.logdNoFile
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MyDroidHttpServer(httpPort: Int,
                        private val aliveTrigger: IDroidServerAliveTrigger,
                        private val globalNetworkMonitor: GlobalNetworkMonitorObj,
    ) : NanoHTTPD(httpPort), KoinComponent {

    private val receiverFlowsObj: GlobalReceiverFlowsObj by inject()

    private val handlers: List<AbsHttpRequestHandler> = listOf(
        CommandRequestHandler(globalNetworkMonitor),
        FileDownloadHandler(),
        StaticAssetHandler(),
        ChunkUploadHandler(receiverFlowsObj),
    )

    init {
        tempFileManagerFactory = MyDroidTempFileMgrFactory()
    }

    override fun serve(session: IHTTPSession): Response {
        // 处理跨域预检请求 (OPTIONS)
        if (session.method == Method.OPTIONS) {
            return handleOptionRequest()
        }

        val ct = ContentType(session.headers["content-type"]).tryUTF8()
        session.headers["content-type"] = ct.contentTypeHeader

        aliveTrigger.updateAliveTs("http ${session.method.name.lowercase()} request")
        val uri = session.uri ?: ""
        logdNoFile { "handle ${session.method} request $uri" }

        for (handler in handlers) {
            val response = handler.tryHandle(session.method, uri, session)
            if (response != null) return response
        }
        return when (session.method) {
            Method.GET -> NanoHTTPD.newFixedLengthResponse(
                Status.NOT_FOUND,
                MIME_PLAINTEXT,
                Globals.getString(com.allan.mydroid.R.string.server_not_support) + "(E01)"
            )
            else -> NanoHTTPD.newFixedLengthResponse(Globals.getString(com.allan.mydroid.R.string.invalid_request_from_appserver))
        }
    }

    private fun handleOptionRequest(): Response {
        val response = newFixedLengthResponse(Status.OK, MIME_PLAINTEXT, "")
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST")
        response.addHeader("Access-Control-Allow-Headers", "Content-Type")
        return response
    }

    override fun stop() {
        logdNoFile { "stop all." }
        super.stop()
    }

    companion object {
        private const val MIME_PLAINTEXT = "text/plain"
    }
}
