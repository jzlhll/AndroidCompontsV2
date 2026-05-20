package com.allan.mydroid.globals

import com.allan.mydroid.beans.wsdata.TextChatMessageBean
import com.au.module_gson.mmkv.mmkvGetArrayList
import com.au.module_gson.mmkv.mmkvSetArrayList

/**
 * 文本对话消息的MMKV备份工具，上限200条，超过2个月自动移除。
 */
object TextChatBackup {
    private const val KEY = "key_text_chat_history"
    private const val MAX_COUNT = 200
    private const val TWO_MONTHS_MS = 2L * 30 * 24 * 60 * 60 * 1000

    fun load(): ArrayList<TextChatMessageBean> {
        val list = mmkvGetArrayList<TextChatMessageBean>(KEY)
        return trim(list)
    }

    fun save(history: List<TextChatMessageBean>) {
        mmkvSetArrayList(KEY, trim(ArrayList(history)))
    }

    private fun trim(list: ArrayList<TextChatMessageBean>): ArrayList<TextChatMessageBean> {
        val cutoff = System.currentTimeMillis() - TWO_MONTHS_MS
        val filtered = list.filter { it.timestamp >= cutoff }
        return if (filtered.size > MAX_COUNT) {
            ArrayList(filtered.takeLast(MAX_COUNT))
        } else {
            ArrayList(filtered)
        }
    }
}
