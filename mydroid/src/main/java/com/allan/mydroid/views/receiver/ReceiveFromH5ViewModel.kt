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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    private val historyMutex = Mutex()
    private val _historyState = MutableStateFlow<StatusState<String>>(StatusState.Loading)
    val historyState: StateFlow<StatusState<String>> = _historyState.asStateFlow()

    init {
        getActionStore().apply {
            reduce(LoadHistoryAction::class.java) {
                viewModelScope.launch {
                    historyMutex.withLock { loadHistory() }
                }
            }
            reduce(WriteHistoryAction::class.java) { action->
                viewModelScope.launch {
                    historyMutex.withLock {
                        try {
                            exportHistoryRepository.writeNewExportHistory(action.newHistoryItem)
                            loadHistory()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            _historyState.value = StatusState.Error(e)
                        }
                    }
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
        try {
            val history = exportHistoryRepository.loadExportHistory()
            _historyState.value = StatusState.Success(history)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _historyState.value = StatusState.Error(e)
        }
    }

}