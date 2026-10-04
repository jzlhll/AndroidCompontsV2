package com.allan.mydroid.state

import com.allan.mydroid.beans.wsdata.TextChatMessageBean
import com.au.module_android.simpleflow.createNoStickyFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 主机聊天历史串行追加和备份，避免 UI 与多个连接线程互相覆盖。 */
class GlobalTextChatObj {
    private val _historyFlow = MutableStateFlow<List<TextChatMessageBean>>(emptyList())
    val historyFlow: StateFlow<List<TextChatMessageBean>> = _historyFlow.asStateFlow()
    val incomingMessageFlow: MutableSharedFlow<TextChatMessageBean> = createNoStickyFlow()

    private var historyLoaded = false

    @Synchronized
    fun loadHistory() {
        if (historyLoaded) return
        _historyFlow.value = TextChatBackup.load()
        historyLoaded = true
    }

    @Synchronized
    fun addMessage(bean: TextChatMessageBean) {
        loadHistory()
        _historyFlow.value += bean
        TextChatBackup.save(_historyFlow.value)
    }
    fun emitIncoming(bean: TextChatMessageBean) { incomingMessageFlow.tryEmit(bean) }
}
