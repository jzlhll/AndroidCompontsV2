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
