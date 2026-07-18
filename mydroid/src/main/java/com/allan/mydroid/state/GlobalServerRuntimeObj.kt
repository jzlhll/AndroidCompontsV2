package com.allan.mydroid.state

import com.allan.mydroid.api.MyDroidMode
import com.au.module_android.simpleflow.StatusState
import com.au.module_android.simpleflow.createStatusStateFlow
import com.au.module_android.simpleflow.setSuccess
import com.au.module_android.simpleflow.setUninitialized
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GlobalServerRuntimeObj {
    private val _serverIsOpenFlow = MutableStateFlow(false)
    val serverIsOpenFlow: StateFlow<Boolean> = _serverIsOpenFlow.asStateFlow()

    private val _currentDroidModeFlow = MutableStateFlow(MyDroidMode.None)
    val currentDroidModeFlow: StateFlow<MyDroidMode> = _currentDroidModeFlow.asStateFlow()

    val portsFlow: MutableStateFlow<StatusState<Pair<Int, Int>>> = createStatusStateFlow()

    fun setServerOpen(open: Boolean) { _serverIsOpenFlow.value = open }
    fun setMode(mode: MyDroidMode) { _currentDroidModeFlow.value = mode }
    fun setPorts(http: Int, ws: Int) { portsFlow.setSuccess(http to ws) }
    fun clearPorts() { portsFlow.setUninitialized() }
}
