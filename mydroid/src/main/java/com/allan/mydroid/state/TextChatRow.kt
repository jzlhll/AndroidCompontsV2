package com.allan.mydroid.state

import com.allan.mydroid.beans.wsdata.TextChatMessageBean
import com.au.module_nested.recyclerview.IMultiViewTypeBean

const val TEXT_CHAT_GAP_MS = 5 * 60 * 1000L

enum class TextChatDividerKind { CREATED, JOINED, GAP }

sealed interface TextChatRow : IMultiViewTypeBean {
    val id: Long
    val timestamp: Long

    data class Message(
        override val id: Long,
        val bean: TextChatMessageBean,
        val isMine: Boolean,
    ) : TextChatRow {
        override val timestamp: Long get() = bean.timestamp
        override val viewType: Int get() = VIEW_TYPE_MESSAGE
    }

    data class Divider(
        override val id: Long,
        override val timestamp: Long,
        val kind: TextChatDividerKind,
    ) : TextChatRow {
        override val viewType: Int get() = VIEW_TYPE_DIVIDER
    }

    companion object {
        const val VIEW_TYPE_MESSAGE = 0
        const val VIEW_TYPE_DIVIDER = 1
    }
}
