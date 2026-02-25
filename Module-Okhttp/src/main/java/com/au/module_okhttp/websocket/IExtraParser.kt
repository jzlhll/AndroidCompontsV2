package com.au.module_okhttp.websocket

import org.json.JSONObject

/**
 * WebSocket额外解析器接口
 */
interface IExtraParser {
    /**
     * 解析额外的WebSocket消息
     * @param text 原始消息文本
     * @param jo 解析后的JSON对象
     */
    fun onExtraParse(text: String, jo: JSONObject)
}