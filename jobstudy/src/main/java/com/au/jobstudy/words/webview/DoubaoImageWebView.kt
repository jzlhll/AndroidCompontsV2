package com.au.jobstudy.words.webview

import android.content.Context
import android.util.AttributeSet
import android.webkit.WebView
import android.webkit.WebViewClient
import com.au.module_android.utils.logdNoFile
import com.github.lzyzsd.jsbridge.BridgeWebView
import com.github.lzyzsd.jsbridge.BridgeWebViewClient

class ClawWebView : BridgeWebView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private lateinit var client: ClawWebViewClient

    override fun generateBridgeWebViewClient(): WebViewClient {
        logdNoFile { "gnerate bridge" }
        client = ClawWebViewClient(this)
        return client
    }

    fun loadDoubaoWebAndAutoFill(url:String, text:String) {
        client.htmlClaw.newFillText = text
        loadUrl(url)
    }
}

class ClawWebViewClient(val webView: ClawWebView) : BridgeWebViewClient(webView) {
    companion object {
        private const val BASE_URL = "https://www.doubao.com/chat/create-image"
    }

    private val successCallback:(Boolean)->Unit = {
        //// 填写第一个文本输入框
    }
    val htmlClaw = DoubaoHtmlClaw(webView).also {
        it.successCallback = successCallback
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        logdNoFile { "onPage finished $url" }
        if (url == BASE_URL) {
            htmlClaw.check()
        }
    }
}
