package com.allan.mydroid.nanohttp.messengers

import android.util.Base64
import com.allan.mydroid.api.WSApisConst.Companion.API_WS_TEXT_CHAT_CALLBACK
import com.allan.mydroid.api.WSApisConst.Companion.API_WS_TEXT_CHAT_SEND
import com.allan.mydroid.beans.wsdata.TextChatMessageBean
import com.allan.mydroid.beans.wsdata.TextChatWsData
import com.allan.mydroid.beans.wsdata.getIconColorByIp
import com.allan.mydroid.nanohttp.AbsWebSocketClientMessenger
import com.allan.mydroid.nanohttp.ServerWebsocketClient
import com.allan.mydroid.state.GlobalTextChatObj
import com.au.module_android.log.logdNoFile
import org.json.JSONObject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.getValue

class WebsocketTextChatModeMessenger(
    client: ServerWebsocketClient,
    val roomId: String?,
) : AbsWebSocketClientMessenger(client), KoinComponent {
    private val textChatObj: GlobalTextChatObj by inject()

    override fun onOpen() {
        val roomId = roomId ?: return
        if (!client.server.isCurrentTextChatRoom(roomId)) return
        for (bean in textChatObj.hostReplaySnapshot()) {
            if (!client.server.isCurrentTextChatRoom(roomId)) return
            val textBase64 = Base64.encodeToString(bean.text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val data = TextChatWsData(textBase64, bean.ip, "", bean.timestamp, bean.iconColor, replayed = true)
            client.sendApiResult(API_WS_TEXT_CHAT_CALLBACK, data)
        }
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
        val roomId = roomId ?: return
        if (!client.server.isCurrentTextChatRoom(roomId)) return
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
            host = "",
            timestamp = json.optLong("timestamp").takeIf { it > 0 } ?: System.currentTimeMillis(),
            iconColor = getIconColorByIp(client.remoteIp),
        )
        textChatObj.addHostMessage(bean, isMine = false) {
            client.server.broadcastTextChatFromApp(bean, roomId, excludedClient = client)
        }
    }
}
