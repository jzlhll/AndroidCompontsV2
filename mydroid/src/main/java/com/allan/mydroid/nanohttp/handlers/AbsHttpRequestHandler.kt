package com.allan.mydroid.nanohttp.handlers

import com.au.module_android.Globals
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response
import org.koin.core.component.KoinComponent

/**
 * HTTP 请求处理器抽象基类。
 * 子类按 method + uri 匹配自行处理；不匹配时返回 null，由路由器继续询问下一个 handler。
 */
abstract class AbsHttpRequestHandler : KoinComponent {

    /**
     * 尝试处理请求；若该方法/uri 不归属本 handler 则返回 null。
     */
    abstract fun tryHandle(method: NanoHTTPD.Method, uri: String, session: IHTTPSession): Response?

    protected fun newNotFoundResponse(msg: String): Response {
        return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, msg)
    }

    protected fun newInternalErrorResponse(msg: String): Response {
        return NanoHTTPD.newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, msg)
    }

    protected fun invalidRequestResponse(): Response {
        return NanoHTTPD.newFixedLengthResponse(Globals.getString(com.allan.mydroid.R.string.invalid_request_from_appserver))
    }

    companion object {
        const val MIME_PLAINTEXT = "text/plain"
        const val MIME_HTML = "text/html"
        const val MIME_JS = "application/javascript"
    }
}
