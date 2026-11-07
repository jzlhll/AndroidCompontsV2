package com.au.agit.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.au.agit.R
import com.au.agit.appString
import com.au.agit.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class LogsUiState(
    val loading: Boolean = false,
    val busy: Boolean = false,
    val files: List<LogFileInfo> = emptyList(),
    val lines: List<LogLine> = emptyList(),
    val olderOffset: Long = 0,
    val sharedFile: File? = null,
    val error: String? = null,
)

/** NavEntry 持有日志快照和分页任务，刷新取消并等待前一读取结束。 */
class LogsViewModel(private val repository: LogRepository, private val feedback: UserFeedback, private val fileName: String?) : ViewModel() {
    private val mutableState = MutableStateFlow(LogsUiState())
    val stateFlow = mutableState.asStateFlow()
    private var readJob: Job? = null

    fun reload(older: Boolean = false) {
        if (mutableState.value.busy) return
        if (older && (mutableState.value.loading || mutableState.value.olderOffset == 0L)) return
        val previous = readJob
        previous?.cancel()
        readJob = viewModelScope.launch {
            previous?.join()
            mutableState.value = mutableState.value.copy(loading = true, error = null)
            try {
                if (fileName == null) {
                    val files = repository.scan()
                    mutableState.value = mutableState.value.copy(loading = false, files = files)
                } else {
                    val page = repository.read(fileName, if (older) mutableState.value.olderOffset else null)
                    mutableState.value = mutableState.value.copy(loading = false, lines = page.lines + if (older) mutableState.value.lines else emptyList(), olderOffset = page.olderOffset)
                }
            } catch (error: CancellationException) { throw error } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(loading = false, error = safeFailure(error).message)
                feedback.failure("log read", error)
            }
        }
    }

    fun delete(name: String) {
        if (mutableState.value.busy) return
        mutableState.value = mutableState.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                readJob?.cancelAndJoin()
                repository.delete(name)
                val files = repository.scan()
                mutableState.value = mutableState.value.copy(files = files, loading = false)
                feedback.notify(appString(R.string.log_deleted))
            } catch (error: CancellationException) { throw error } catch (error: Exception) { feedback.failure("log delete", error) }
            finally { mutableState.value = mutableState.value.copy(busy = false, loading = false) }
        }
    }

    fun share(names: Set<String>) {
        if (mutableState.value.busy || mutableState.value.sharedFile != null) return
        mutableState.value = mutableState.value.copy(busy = true)
        viewModelScope.launch {
            try {
                val file = repository.export(names)
                mutableState.value = mutableState.value.copy(sharedFile = file)
            } catch (error: CancellationException) { throw error } catch (error: Exception) { feedback.failure("log export", error) }
            finally { mutableState.value = mutableState.value.copy(busy = false) }
        }
    }

    fun acknowledgeShare() { mutableState.value = mutableState.value.copy(sharedFile = null) }
}
