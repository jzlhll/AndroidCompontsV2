package com.allan.mydroid.nanohttp

import com.allan.mydroid.apk.ApkReceiveSession
import com.allan.mydroid.api.MyDroidMode
import com.allan.mydroid.globals.IDroidServerAliveTrigger
import com.allan.mydroid.globals.nanoTempCacheChunksDir
import com.allan.mydroid.globals.nanoTempCacheDir
import com.allan.mydroid.nanohttp.handlers.AbsHttpRequestHandler
import com.allan.mydroid.nanohttp.handlers.ChunkUploadHandler
import com.allan.mydroid.nanohttp.handlers.CommandRequestHandler
import com.allan.mydroid.nanohttp.handlers.FileDownloadHandler
import com.allan.mydroid.nanohttp.handlers.StaticAssetHandler
import com.allan.mydroid.nanohttp.handlers.UploadRequestHandler
import com.allan.mydroid.network.GlobalNetworkMonitorObj
import com.allan.mydroid.repository.TransferFiles
import com.au.module_android.Globals
import com.au.module_android.log.logdNoFile
import com.au.module_android.log.logEx
import com.au.module_android.utils.launchOnIOThread
import com.au.module_okhttp.api.ResultBean
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import org.json.JSONException
import java.io.File

class MyDroidHttpServer(httpPort: Int,
                        private val aliveTrigger: IDroidServerAliveTrigger,
                        private val globalNetworkMonitor: GlobalNetworkMonitorObj,
                        mode: MyDroidMode,
    ) : NanoHTTPD(httpPort) {

    private val apkSession = if (mode == MyDroidMode.InstallApk) ApkReceiveSession() else null
    private val requestTempFileFactory = MyDroidTempFileMgrFactory(apkSession?.requestDir ?: File(nanoTempCacheDir()))
    private val chunkUploadHandler = ChunkUploadHandler(
        chunksDir = apkSession?.chunksDir ?: File(nanoTempCacheChunksDir()),
        stagingDir = apkSession?.stagingDir ?: TransferFiles.stagingDir,
        requiredExtension = if (apkSession == null) null else "apk",
    )

    private val handlers: List<AbsHttpRequestHandler> = buildList {
        add(CommandRequestHandler(globalNetworkMonitor, apkOnly = apkSession != null))
        if (apkSession == null) {
            add(FileDownloadHandler(aliveTrigger))
            add(StaticAssetHandler())
        }
        add(UploadRequestHandler(chunkUploadHandler, aliveTrigger, apkSession))
    }

    init {
        tempFileManagerFactory = requestTempFileFactory
    }

    override fun serve(session: IHTTPSession): Response {
        // 处理跨域预检请求 (OPTIONS)
        if (apkSession == null && session.method == Method.OPTIONS) {
            return handleOptionRequest()
        }

        val origin = session.headers["origin"]
        if (apkSession != null && session.method == Method.POST && origin != null &&
            !origin.equals("http://${session.headers["host"]}", ignoreCase = true)) {
            return newFixedLengthResponse(Status.FORBIDDEN, MIME_PLAINTEXT, "Invalid origin").also {
                it.addHeader("Connection", "close")
            }
        }

        val ct = ContentType(session.headers["content-type"]).tryUTF8()
        session.headers["content-type"] = ct.contentTypeHeader

        aliveTrigger.updateAliveTs("http ${session.method.name.lowercase()} request")
        val uri = session.uri ?: ""
        logdNoFile { "handle ${session.method} request $uri" }

        try {
            for (handler in handlers) {
                val response = handler.tryHandle(session.method, uri, session)
                if (response != null) return response
            }
        } catch (e: Exception) {
            logEx(throwable = e) { "Handle HTTP request failed: $uri" }
            val status = when (e) {
                is JSONException, is IllegalArgumentException -> Status.BAD_REQUEST
                is ResponseException -> e.status
                else -> Status.INTERNAL_ERROR
            }
            return ResultBean<Unit>(CODE_FAIL,
                Globals.getString(com.allan.mydroid.R.string.something_error), null)
                .jsonResponse(status).also {
                    // 请求体可能未完整消费，不能把残余字节当成下一条请求。
                    it.addHeader("Connection", "close")
                }
        }
        return when (session.method) {
            Method.GET -> NanoHTTPD.newFixedLengthResponse(
                Status.NOT_FOUND,
                MIME_PLAINTEXT,
                Globals.getString(com.allan.mydroid.R.string.server_not_support) + "(E01)"
            )
            else -> NanoHTTPD.newFixedLengthResponse(Status.BAD_REQUEST, MIME_PLAINTEXT,
                Globals.getString(com.allan.mydroid.R.string.invalid_request_from_appserver))
                .also { it.addHeader("Connection", "close") }
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
        apkSession?.close()
        requestTempFileFactory.close()
        val pendingFiles = chunkUploadHandler.close()
        try {
            super.stop()
        } finally {
            if (apkSession != null) {
                apkSession.clearTemporaryFiles()
            } else if (pendingFiles.isNotEmpty()) {
                Globals.mainScope.launchOnIOThread { pendingFiles.forEach { it.delete() } }
            }
        }
    }

    companion object {
        private const val MIME_PLAINTEXT = "text/plain"
    }
}
