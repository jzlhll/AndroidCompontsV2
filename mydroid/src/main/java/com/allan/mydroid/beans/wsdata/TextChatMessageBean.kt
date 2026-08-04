package com.allan.mydroid.beans.wsdata

import androidx.annotation.Keep
import com.au.module_nested.recyclerview.IViewTypeBean

@Keep
data class TextChatMessageBean(
    val text: String,
    val ip: String,
    val host: String,
    val timestamp: Long,
    val iconColor: String,
) : IViewTypeBean

@Keep
data class TextChatWsData(
    val textBase64: String,
    val ip: String,
    val host: String,
    val timestamp: Long,
    val iconColor: String,
)

/** IP 哈希取色板，与 HTML 端 mydroid-text-chat.js 保持一致。 */
private val chatColorPalette = listOf(
    "#3D7C42", "#FF9E80", "#6A3188", "#895DF8",
    "#CEBE55", "#5CCE99", "#CE626E", "#71A3CE",
)

/** 根据 IP 哈希计算聊天头像颜色，确保客户端与 HTML 端颜色一致。 */
fun getIconColorByIp(ip: String): String {
    var hash = 0
    for (c in ip) {
        hash = ((hash shl 5) - hash) + c.code
    }
    val absHash = if (hash == Int.MIN_VALUE) 2147483648L else kotlin.math.abs(hash.toLong())
    return chatColorPalette[(absHash % chatColorPalette.size).toInt()]
}
