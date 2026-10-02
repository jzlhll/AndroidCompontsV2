package childmonitor.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import childmonitor.domain.MonitorRuntime
import childmonitor.model.*
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 页面取消订阅不取消应用已接受的命令，界面状态只在主线程更新。 */
class MonitorViewModel(val runtime: MonitorRuntime) : ViewModel() {
    private val mutableSnapshotFlow = MutableStateFlow(runtime.snapshotFlow.value)
    val snapshotFlow = mutableSnapshotFlow.asStateFlow()
    init {
        viewModelScope.launch { runtime.snapshotFlow.collect {
            if (it.revision > mutableSnapshotFlow.value.revision) mutableSnapshotFlow.value = it
        } }
    }
    fun refreshStorage() { viewModelScope.launch { runtime.refreshStorage(UUID.randomUUID().toString()) } }
    fun command(block: suspend (String) -> Unit) { viewModelScope.launch { block(UUID.randomUUID().toString()) } }
}
