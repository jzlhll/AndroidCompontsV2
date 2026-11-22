package com.allan.mydroid.views.chat

import android.content.Context
import com.allan.mydroid.R
import com.allan.mydroid.state.TextChatDividerKind
import com.allan.mydroid.state.TextChatRow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dividerTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

fun TextChatRow.Divider.displayText(context: Context): String {
    val time = dividerTimeFormatter.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
    return when (kind) {
        TextChatDividerKind.CREATED -> context.getString(R.string.text_chat_divider_created, time)
        TextChatDividerKind.JOINED -> context.getString(R.string.text_chat_divider_joined, time)
        TextChatDividerKind.GAP -> time
    }
}
