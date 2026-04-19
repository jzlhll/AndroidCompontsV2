package com.allan.mydroid.nanohttp.wsmsger

import android.util.Base64
import com.allan.mydroid.api.WSApisConst.Companion.API_WS_TEXT_CHAT_SEND
import com.allan.mydroid.beans.wsdata.TextChatMessageBean
import com.allan.mydroid.globals.MyDroidConst
import com.allan.mydroid.nanohttp.AbsWebSocketClientMessenger
import com.allan.mydroid.nanohttp.WebsocketClientInServer
import com.au.module_android.log.logdNoFile
import org.json.JSONObject

class WebsocketTextChatModeMessenger(client: WebsocketClientInServer) : AbsWebSocketClientMessenger(client) {
    override fun onOpen() {
    }

    override fun onClose() {
    }

    override fun onMessage(origJsonStr: String, api: String, json: JSONObject) {
        when (api) {
            API_WS_TEXT_CHAT_SEND -> onTextChatMessage(json)
        }
    }

    // 处理浏览器发来的文本消息。
    private fun onTextChatMessage(json: JSONObject) {
        val textBase64 = json.optString("textBase64")
        if (textBase64.isEmpty()) {
            return
        }
        val text = try {
            String(Base64.decode(textBase64, Base64.DEFAULT), Charsets.UTF_8)
        } catch (e: Exception) {
            logdNoFile { "text chat decode error ${e.message}" }
            return
        }
        if (text.isBlank()) {
            return
        }

        val bean = TextChatMessageBean(
            text = text,
            ip = client.remoteIp,
            host = client.hostName,
            timestamp = json.optLong("timestamp").takeIf { it > 0 } ?: System.currentTimeMillis(),
            iconColor = json.optString("iconColor").ifBlank { client.color },
        )
        MyDroidConst.textChatHistory.add(bean)
        MyDroidConst.textChatIncomingData.setValueSafe(bean)
    }
}
