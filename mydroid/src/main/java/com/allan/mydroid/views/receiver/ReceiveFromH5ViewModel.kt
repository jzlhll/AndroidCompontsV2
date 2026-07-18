package com.allan.mydroid.views.receiver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allan.mydroid.repository.ExportHistoryRepository
import com.allan.mydroid.repository.GlobalFileListRepoObj
import com.au.module_android.simpleflow.ActionDispatcherImpl
import com.au.module_android.simpleflow.IActionDispatcher
import com.au.module_android.simpleflow.IStateAction
import com.au.module_android.simpleflow.StatusState
import com.au.module_android.utils.launchOnThread
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ReceiveFromH5ViewModel : ViewModel(), IActionDispatcher by ActionDispatcherImpl(), KoinComponent {
    private val exportHistoryRepository: ExportHistoryRepository by inject()
    private val fileListRepository: GlobalFileListRepoObj by inject()
    class LoadHistoryAction : IStateAction
    class WriteHistoryAction(val newHistoryItem: String) : IStateAction
    class LoadFileListAction : IStateAction

    private val _historyState = MutableStateFlow<StatusState<String>>(StatusState.Loading)
    val historyState: StateFlow<StatusState<String>> = _historyState.asStateFlow()

    init {
        getActionStore().apply {
            reduce(LoadHistoryAction::class.java) {
                viewModelScope.launchOnThread {
                    loadHistory()
                }
            }
            reduce(WriteHistoryAction::class.java) { action->
                viewModelScope.launchOnThread {
                    exportHistoryRepository.writeNewExportHistory(action.newHistoryItem)
                    delay(100)
                    loadHistory()
                }
            }
            reduce(LoadFileListAction::class.java) { action ->
                viewModelScope.launchOnThread {
                    fileListRepository.reloadFileList()
                }
            }
        }
    }

    private suspend fun loadHistory() {
        val history = exportHistoryRepository.loadExportHistory()
        _historyState.value = StatusState.Success(history)
    }

}