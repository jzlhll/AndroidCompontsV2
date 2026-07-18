package com.allan.mydroid.nanohttp.messengers

import com.allan.mydroid.nanohttp.AbsWebSocketClientMessenger
import com.allan.mydroid.nanohttp.ServerWebsocketClient
import org.json.JSONObject

class WebsocketNoneModeMessenger(client: ServerWebsocketClient) : AbsWebSocketClientMessenger(client) {
    override fun onOpen() {
    }

    override fun onClose() {
    }

    override fun onMessage(origJsonStr:String, api:String, json: JSONObject) {
    }
}
