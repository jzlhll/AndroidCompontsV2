package com.allan.mydroid.state

import com.allan.mydroid.beansinner.WebSocketClientInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GlobalClientListFlowsObj {
    private val _clientListFlow = MutableStateFlow<List<WebSocketClientInfo>>(emptyList())
    val clientListFlow: StateFlow<List<WebSocketClientInfo>> = _clientListFlow.asStateFlow()

    fun set(list: List<WebSocketClientInfo>) { _clientListFlow.value = list }
    fun clear() { _clientListFlow.value = emptyList() }
}