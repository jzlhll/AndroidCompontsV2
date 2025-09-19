package com.allan.mydroid.views.receiver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allan.mydroid.beansinner.MergedFileInfo
import com.allan.mydroid.globals.MyDroidMess
import com.au.module_android.simpleflow.ActionDispatcherImpl
import com.au.module_android.simpleflow.IActionDispatcher
import com.au.module_android.simpleflow.IStateAction
import com.au.module_android.simpleflow.StatusState
import com.au.module_android.utils.launchOnThread
import com.au.module_android.utils.logd
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ReceiveFromH5ViewModel : ViewModel(), IActionDispatcher by ActionDispatcherImpl() {
    class LoadHistoryAction : IStateAction
    class WriteHistoryAction(val newHistoryItem: String) : IStateAction
    class LoadFileListAction : IStateAction

    private val _historyState = MutableStateFlow<StatusState<String>>(StatusState.Loading)
    val historyState: StateFlow<StatusState<String>> = _historyState.asStateFlow()

    private val _fileListState = MutableStateFlow<StatusState<List<MergedFileInfo>>>(StatusState.Loading)
    val fileListState: StateFlow<StatusState<List<MergedFileInfo>>> = _fileListState.asStateFlow()

    init {
        getActionStore().apply {
            reduce(LoadHistoryAction::class.java) {
                viewModelScope.launchOnThread {
                    loadHistory()
                }
            }
            reduce(WriteHistoryAction::class.java) { action->
                viewModelScope.launchOnThread {
                    MyDroidMess().writeNewExportHistory(action.newHistoryItem)
                    delay(100)
                    loadHistory()
                }
            }
            reduce(LoadFileListAction::class.java) { action ->
                viewModelScope.launchOnThread {
                    val fileList = MyDroidMess().loadFileList()
                    logd { "load file list: $fileList" }
                    delay(100)
                    _fileListState.value = StatusState.Success(fileList)
                }
            }
        }
    }

    private suspend fun loadHistory() {
        val history = MyDroidMess().loadExportHistory()
        _historyState.value = StatusState.Success(history)
    }

}