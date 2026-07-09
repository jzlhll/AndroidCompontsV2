package com.au.module_okhttp.websocket

import org.json.JSONObject

/**
 * 设备 push 额外解析器接口
 * 承接 WebSocket live connection push 与 MQTT app-rpc publish。
 */
interface IExtraParser {
    /**
     * 解析设备 push 消息。
     * @param nameTag 设备标识，通常是 frameId
     * @param text 原始消息文本
     * @param jo 解析后的JSON对象
     */
    fun onExtraParse(nameTag: String, text: String, jo: JSONObject)
}
