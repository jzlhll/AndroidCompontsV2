package childmonitor.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import childmonitor.data.repository.MonitorRepository
import childmonitor.domain.MonitorRuntime
import childmonitor.model.CommandResult
import childmonitor.model.SessionCursor
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

data class RecordUiState(
    val result: MonitorRepository.Result? = null,
    val items: List<MonitorRepository.Summary> = emptyList(),
    val cursor: SessionCursor? = null,
    val loaded: Boolean = false,
    val busy: Boolean = false,
    val failed: Boolean = false,
    val operationFailed: Boolean = false,
    val selected: Set<String> = emptySet(),
    val celebration: Boolean = false,
    val storage: childmonitor.model.StorageOverview? = null,
)

/** 独立管理记录查询与选择状态，失败时保留已显示的数据。 */
class RecordViewModel(val runtime: MonitorRuntime) : ViewModel() {
    private val mutableStateFlow = MutableStateFlow(RecordUiState())
    val stateFlow = mutableStateFlow.asStateFlow()
    private var pendingReload: (() -> Unit)? = null
    private var celebrationClaimedId: String? = null
    fun loadResult(id: String, celebrate: Boolean = false) {
        if (stateFlow.value.busy) { pendingReload = { loadResult(id, celebrate) }; return }
        task {
            val result = runtime.records.readResult(id)
            var celebration = stateFlow.value.celebration && stateFlow.value.result?.session?.id == id
            if (celebrate && result != null && celebrationClaimedId != id) {
                val claim = runtime.claimCelebration(id, UUID.randomUUID().toString())
                if (claim is CommandResult.Success) {
                    celebrationClaimedId = id
                    celebration = claim.data == true
                }
            }
            mutableStateFlow.value = stateFlow.value.copy(result = result, loaded = true,
                celebration = result != null && celebration)
        }
    }
    fun loadStorage() {
        if (stateFlow.value.busy) { pendingReload = { loadStorage() }; return }
        task {
        val result = runtime.storageOverview(UUID.randomUUID().toString())
        if (result is CommandResult.Success) mutableStateFlow.value = stateFlow.value.copy(storage = result.data)
        else mutableStateFlow.value = stateFlow.value.copy(failed = true)
    }
    }
    fun purge(ids: Set<String>, protectedId: String?) = task {
        val result = runtime.purgeVideos(ids.toList(), protectedId, UUID.randomUUID().toString())
        val failed = (result as? CommandResult.Success<List<String>>)?.data?.toSet() ?: ids
        mutableStateFlow.value = stateFlow.value.copy(selected = failed, operationFailed = failed.isNotEmpty())
    }
    fun dismissCelebration() { mutableStateFlow.value = stateFlow.value.copy(celebration = false) }
    fun keep(id: String, onDone: () -> Unit) = task {
        val result = runtime.keepStatistics(id, UUID.randomUUID().toString())
        if (result is CommandResult.Success) onDone()
        else mutableStateFlow.value = stateFlow.value.copy(failed = true)
    }
    fun clearSelection() { mutableStateFlow.value = stateFlow.value.copy(selected = emptySet()) }
    fun loadAlbum(reset: Boolean = false) = task {
        val page = runtime.records.querySessions(if (reset) null else stateFlow.value.cursor)
        mutableStateFlow.value = stateFlow.value.copy(items = if (reset) page.items else (stateFlow.value.items + page.items).distinctBy { it.session.id },
            cursor = page.nextCursor, loaded = true)
    }
    fun refreshAlbum(protectedId: String?) {
        if (stateFlow.value.busy) { pendingReload = { refreshAlbum(protectedId) }; return }
        task {
            // 重读已加载范围，保持分页深度与稳定条目身份，避免返回时掉回第一页。
            val targetCount = if (stateFlow.value.items.size > 30) stateFlow.value.items.size else 30
            val items = mutableListOf<MonitorRepository.Summary>()
            var cursor: SessionCursor? = null
            do {
                val page = runtime.records.querySessions(cursor)
                items += page.items
                cursor = page.nextCursor
            } while (cursor != null && items.size < targetCount)
            val selected = stateFlow.value.selected
            val validSelection = if (selected.isEmpty()) selected else selected.intersect(runtime.records.queryDeletableIds(protectedId).toSet())
            mutableStateFlow.value = stateFlow.value.copy(items = items, cursor = cursor, selected = validSelection, loaded = true)
        }
    }
    fun select(id: String) {
        if (!stateFlow.value.busy) mutableStateFlow.value = stateFlow.value.copy(selected =
            if (id in stateFlow.value.selected) stateFlow.value.selected - id else stateFlow.value.selected + id)
    }
    fun selectAll(protectedId: String?) = task {
        mutableStateFlow.value = stateFlow.value.copy(selected = runtime.records.queryDeletableIds(protectedId).toSet())
    }
    fun delete(ids: Set<String>, protectedId: String?, onDeleted: (Set<String>) -> Unit) = task {
        val result = runtime.deleteSessions(ids.toList(), protectedId, UUID.randomUUID().toString())
        val failed = (result as? CommandResult.Success<List<String>>)?.data?.toSet() ?: ids
        val deleted = ids - failed
        mutableStateFlow.value = stateFlow.value.copy(items = stateFlow.value.items.filter { it.session.id !in deleted }, selected = failed, failed = failed.isNotEmpty())
        onDeleted(deleted)
    }
    fun retry(id: String) = task {
        val result = runtime.retrySave(id, UUID.randomUUID().toString())
        mutableStateFlow.value = stateFlow.value.copy(result = runtime.records.readResult(id), failed = result is CommandResult.Failure)
    }
    private fun task(block: suspend () -> Unit) {
        if (stateFlow.value.busy) return
        mutableStateFlow.value = stateFlow.value.copy(busy = true, failed = false)
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutableStateFlow.value = stateFlow.value.copy(failed = true) }
            finally {
                mutableStateFlow.value = stateFlow.value.copy(busy = false)
                val reload = pendingReload
                pendingReload = null
                if (isActive) reload?.invoke()
            }
        }
    }
}
