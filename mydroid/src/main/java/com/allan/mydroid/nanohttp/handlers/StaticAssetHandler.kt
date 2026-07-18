package com.allan.mydroid.nanohttp.handlers

import com.allan.mydroid.api.MyDroidMode
import com.allan.mydroid.state.GlobalServerRuntimeObj
import com.au.module_android.Globals
import com.au.module_android.log.logdNoFile
import com.modulenative.AppNative
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response
import org.koin.core.component.inject
import java.io.FileNotFoundException

/**
 * 提供 html/js 静态资源文件服务。
 * - GET /            -> 根据 currentDroidMode 返回对应主页
 * - GET *.html       -> transfer 目录下的 html
 * - GET *.js         -> transfer 目录下的 js
 */
class StaticAssetHandler : AbsHttpRequestHandler() {

    private val serverRuntimeState: GlobalServerRuntimeObj by inject()

    override fun tryHandle(method: NanoHTTPD.Method, uri: String, session: IHTTPSession): Response? {
        if (method != NanoHTTPD.Method.GET) return null

        when {
            uri == "/" -> return serveIndexPage()
            uri.endsWith(".html") -> {
                val name = uri.substring(1)
                return serveAssetFile("transfer/$name", MIME_HTML)
            }
            uri.endsWith(".js") -> {
                val name = uri.substring(1)
                return serveAssetFile("transfer/$name", MIME_JS)
            }
        }
        return null
    }

    private fun serveIndexPage(): Response {
        val error: String
        when (serverRuntimeState.currentDroidModeFlow.value) {
            MyDroidMode.Send -> {
                return serveAssetFile("transfer/ReceiveFromPhone.html", MIME_HTML)
            }
            MyDroidMode.Receiver -> {
                return serveAssetFile("transfer/SendToPhone.html", MIME_HTML)
            }
            MyDroidMode.TextChat -> {
                return serveAssetFile("transfer/TextChat.html", MIME_HTML)
            }
            else -> {
                error = Globals.getString(com.allan.mydroid.R.string.server_not_support) + "(E02)"
                return newNotFoundResponse(error)
            }
        }
    }

    private fun serveAssetFile(assetFile: String, mimeType: String): Response {
        return try {
            val text = AppNative.asts(Globals.app, assetFile)
            logdNoFile { "serve Asset File read success $assetFile." }
            NanoHTTPD.newFixedLengthResponse(Response.Status.OK, mimeType, text)
        } catch (_: FileNotFoundException) {
            NanoHTTPD.newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                """"{"error": "File $assetFile not found"}"""
            )
        }
    }
}
