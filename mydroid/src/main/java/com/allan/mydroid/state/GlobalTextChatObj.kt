package com.allan.mydroid.state

import com.allan.mydroid.beans.wsdata.TextChatMessageBean
import com.allan.mydroid.globals.TextChatBackup
import com.au.module_android.simpleflow.createNoStickyFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GlobalTextChatObj {
    private val _historyFlow = MutableStateFlow<List<TextChatMessageBean>>(emptyList())
    val historyFlow: StateFlow<List<TextChatMessageBean>> = _historyFlow.asStateFlow()
    val incomingMessageFlow: MutableSharedFlow<TextChatMessageBean> = createNoStickyFlow()

    fun loadHistory() { if (_historyFlow.value.isEmpty()) _historyFlow.value = TextChatBackup.load() }
    fun addMessage(bean: TextChatMessageBean) {
        _historyFlow.value = _historyFlow.value + bean
        TextChatBackup.save(_historyFlow.value)
    }
    fun emitIncoming(bean: TextChatMessageBean) { incomingMessageFlow.tryEmit(bean) }
}
