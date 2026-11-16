package com.allan.picnote.ui

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.allan.picnote.PicNoteApplication
import com.allan.picnote.logEx
import com.allan.picnote.logd
import com.allan.picnote.data.PermissionHistory
import com.allan.picnote.data.WelcomeDocumentId
import com.allan.picnote.data.NodeGuideKind
import com.allan.picnote.data.entity.*
import com.allan.picnote.data.repositories.DocumentRepository
import com.allan.picnote.data.repositories.Saved
import com.allan.picnote.media.*
import com.au.module_androiduiex.styles.ComposeTheme
import com.au.module_android.utils.withIOThread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import java.io.File

data class HomeState(
    val loading: Boolean = true,
    val documents: List<DocumentSummary> = emptyList(),
    val content: DocumentContent? = null,
    val access: PhotoAccess = PhotoAccess.Denied,
    val scanning: Boolean = false,
    val scanSummary: ScanSummary? = null,
    val scanError: String? = null,
    val pending: List<PendingMediaEntity> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
    val mirrorFailed: Boolean = false,
    val exportedFile: File? = null,
    val permissionHistory: PermissionHistory? = null,
    val permissionHistoryLoading: Boolean = true,
    val permissionHistoryFailed: Boolean = false,
    val permissionPromptVisible: Boolean = false,
    val permissionPromptSaving: Boolean = false,
    val permissionRequestPending: Boolean = false,
    val theme: ComposeTheme? = null,
    val themeSaving: Boolean = false,
    val nodeGuide: NodeGuide? = null,
    val nodeGuideSaving: Boolean = false,
)

/** 连接首页操作与本地存储，真实前台检查按次排队，写操作先等待旧检查退出。 */
class PicNoteViewModel(private val app: PicNoteApplication) : ViewModel() {
    val repository: DocumentRepository = app.documents
    private val mutableStateFlow = MutableStateFlow(HomeState(access = photoAccess(app)))
    val stateFlow = mutableStateFlow.asStateFlow()
    private var scanJob: Job? = null
    private var reloadJob: Job? = null
    private val pendingForegroundChecks = ArrayDeque<Int>()
    private val mirrorFailures = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            try {
                val theme = app.appearancePreferences.readTheme()
                mutableStateFlow.update { it.copy(theme = theme) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                logEx(throwable = e, message = "Read appearance failed")
                mutableStateFlow.update { it.copy(theme = ComposeTheme.Sage, message = "读取外观失败，已使用 Sage") }
            }
        }
        viewModelScope.launch {
            try {
                val history = app.permissionPreferences.read()
                mutableStateFlow.update { it.copy(permissionHistory = history, permissionHistoryLoading = false) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                logEx(throwable = e, message = "Read image permission history failed")
                mutableStateFlow.update { it.copy(permissionHistoryLoading = false, permissionHistoryFailed = true, message = "读取权限提示状态失败") }
            }
        }
        reloadJob = viewModelScope.launch {
            try {
                repository.initializeWelcome()?.let { if (it.mirrorFailed) mirrorFailures.add(it.value) }
                mirrorFailures.addAll(repository.cleanup())
                reload()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure(e)
                mutableStateFlow.update { it.copy(loading = false) }
            } finally {
                if (coroutineContext.isActive) startNextForegroundCheck()
            }
        }
    }

    private fun checkNodeGuideOnStartup() {
        viewModelScope.launch {
            try {
                if (!withIOThread { app.nodeGuidePreferences.hasUnfinishedGuide() }) return@launch
                reloadJob?.join()
                val opened = stateFlow.value.content
                val loaded = !stateFlow.value.loading
                val guide = withIOThread {
                    val eligible = loaded && opened != null && opened.document.id != WelcomeDocumentId && opened.nodes.size >= 4
                    app.nodeGuidePreferences.guideForStartup(eligible)?.let { kind -> NodeGuide(checkNotNull(opened).document.id, kind) }
                }
                if (guide != null) {
                    val current = stateFlow.value.content
                    if (current?.document?.id == guide.documentId && current.nodes.size >= 4) {
                        mutableStateFlow.update { it.copy(nodeGuide = guide) }
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { logEx(throwable = e, message = "Read node guide failed") }
        }
    }

    fun recordNodeClick(kind: NodeGuideKind) {
        viewModelScope.launch {
            try { app.nodeGuidePreferences.recordClick(kind) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { logEx(throwable = e, message = "Save node click failed kind=$kind") }
        }
    }

    fun acknowledgeNodeGuide() {
        val guide = stateFlow.value.nodeGuide ?: return
        if (stateFlow.value.nodeGuideSaving) return
        mutableStateFlow.update { it.copy(nodeGuideSaving = true) }
        viewModelScope.launch {
            try {
                app.nodeGuidePreferences.acknowledgeGuide(guide.kind)
                mutableStateFlow.update { it.copy(nodeGuide = null) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                logEx(throwable = e, message = "Save node guide acknowledgement failed kind=${guide.kind}")
                mutableStateFlow.update { it.copy(message = "保存引导状态失败，请重试") }
            } finally { mutableStateFlow.update { it.copy(nodeGuideSaving = false) } }
        }
    }

    fun setTheme(theme: ComposeTheme) {
        val previous = stateFlow.value.theme ?: return
        if (stateFlow.value.themeSaving || previous == theme) return
        mutableStateFlow.update { it.copy(theme = theme, themeSaving = true) }
        viewModelScope.launch {
            try {
                app.appearancePreferences.saveTheme(theme)
                mutableStateFlow.update { it.copy(themeSaving = false) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                logEx(throwable = e, message = "Save appearance failed")
                mutableStateFlow.update { it.copy(theme = previous, themeSaving = false, message = "外观保存失败，已恢复原主题") }
            }
        }
    }

    fun showPermissionPrompt() {
        val state = stateFlow.value
        if (state.permissionHistory == null || state.permissionHistoryLoading || state.permissionPromptSaving || state.permissionPromptVisible || state.permissionRequestPending) return
        mutableStateFlow.update { it.copy(permissionPromptSaving = true, permissionHistoryFailed = false) }
        viewModelScope.launch {
            try {
                val history = app.permissionPreferences.markExplanationSeen()
                mutableStateFlow.update { it.copy(permissionHistory = history, permissionPromptSaving = false, permissionPromptVisible = true) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                logEx(throwable = e, message = "Save image permission explanation failed")
                mutableStateFlow.update { it.copy(permissionPromptSaving = false, permissionHistoryFailed = true, message = "保存权限提示状态失败，请重试") }
            }
        }
    }

    fun dismissPermissionPrompt() {
        if (!stateFlow.value.permissionPromptSaving) mutableStateFlow.update { it.copy(permissionPromptVisible = false) }
    }

    fun requestImagePermission() {
        val state = stateFlow.value
        if (!state.permissionPromptVisible || state.permissionPromptSaving || state.permissionRequestPending) return
        mutableStateFlow.update { it.copy(permissionPromptSaving = true) }
        viewModelScope.launch {
            try {
                val history = app.permissionPreferences.markRequested()
                mutableStateFlow.update { it.copy(permissionHistory = history, permissionPromptSaving = false,
                    permissionPromptVisible = false, permissionRequestPending = true) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                logEx(throwable = e, message = "Save image permission request failed")
                mutableStateFlow.update { it.copy(permissionPromptSaving = false, permissionHistoryFailed = true, message = "保存权限请求状态失败，请重试") }
            }
        }
    }

    fun consumePermissionRequest(): Boolean {
        if (!stateFlow.value.permissionRequestPending) return false
        mutableStateFlow.update { it.copy(permissionRequestPending = false) }
        return true
    }

    fun updatePhotoAccess() {
        val access = photoAccess(app)
        mutableStateFlow.update { it.copy(access = access, pending = if (access == PhotoAccess.Full) it.pending else emptyList(),
            scanSummary = if (access == PhotoAccess.Full) it.scanSummary else null) }
    }

    fun checkOnForeground(ordinal: Int) {
        if (ordinal == 1) checkNodeGuideOnStartup()
        pendingForegroundChecks.addLast(ordinal)
        logd("event=image_check_queued startupId=${app.startupId} pid=${app.processId} foregroundOrdinal=$ordinal busy=${stateFlow.value.busy} loading=${stateFlow.value.loading}")
        startNextForegroundCheck()
    }

    private fun startNextForegroundCheck() {
        if (stateFlow.value.busy || stateFlow.value.loading || scanJob != null) return
        val ordinal = pendingForegroundChecks.removeFirstOrNull() ?: return
        scanJob = viewModelScope.launch {
            val started = SystemClock.elapsedRealtime()
            val fields = "startupId=${app.startupId} pid=${app.processId} foregroundOrdinal=$ordinal"
            var status = "failed"
            var added = 0
            val access = photoAccess(app)
            logd("event=image_check_start $fields access=$access")
            mutableStateFlow.update { it.copy(access = access, scanning = true, scanError = null,
                pending = if (access == PhotoAccess.Full) it.pending else emptyList()) }
            try {
                if (access == PhotoAccess.Full) {
                    if (stateFlow.value.scanSummary == null) {
                        val previousSummary = app.scanner.previousSummary()
                        mutableStateFlow.update { it.copy(scanSummary = previousSummary) }
                    }
                    val result = app.scanner.scan()
                    added = result.added
                    val openedContent = stateFlow.value.content
                    val refreshedContent = openedContent?.let { repository.contentIfExists(it.document.id) }
                    val pending = app.scanner.pending()
                    status = "success"
                    logd("event=image_check_result $fields baseline=${result.baseline} pending=${pending.size}")
                    mutableStateFlow.update { it.copy(scanSummary = result, pending = pending, scanning = false,
                        content = if (it.content === openedContent && refreshedContent != null) refreshedContent else it.content) }
                } else {
                    app.scanner.permissionLost()
                    status = "skipped"
                    mutableStateFlow.update { it.copy(scanSummary = null, scanning = false) }
                }
            } catch (e: CancellationException) {
                status = "cancelled"
                throw e
            } catch (e: Exception) {
                logEx(throwable = e, message = "Image check failed $fields")
                val currentAccess = photoAccess(app)
                var error = e.message ?: "图片扫描失败，请重试"
                if (access == PhotoAccess.Full && currentAccess != PhotoAccess.Full) {
                    try {
                        app.scanner.permissionLost()
                    } catch (failure: CancellationException) { throw failure }
                    catch (failure: Exception) {
                        logEx(throwable = failure, message = "Invalidate media baseline failed")
                        error = "图片权限已变化，基线状态保存失败，请重试"
                    }
                }
                mutableStateFlow.update { it.copy(access = currentAccess, scanning = false, scanError = error,
                    pending = if (currentAccess == PhotoAccess.Full) it.pending else emptyList()) }
            } finally {
                if (!coroutineContext.isActive) status = "cancelled"
                logd("event=image_check_end $fields status=$status access=${photoAccess(app)} added=$added elapsedMs=${SystemClock.elapsedRealtime() - started}")
                mutableStateFlow.update { it.copy(scanning = false) }
                scanJob = null
                if (coroutineContext.isActive) startNextForegroundCheck()
            }
        }
    }

    fun reloadDocument(id: String? = stateFlow.value.content?.document?.id) {
        if (stateFlow.value.busy) return
        val previous = reloadJob
        val pendingScan = scanJob
        previous?.cancel()
        pendingScan?.cancel()
        mutableStateFlow.update { it.copy(loading = true, scanning = false) }
        reloadJob = viewModelScope.launch {
            withContext(NonCancellable) { previous?.cancelAndJoin(); pendingScan?.cancelAndJoin() }
            coroutineContext.ensureActive()
            try { reload(id) } catch (e: CancellationException) { throw e }
            catch (e: Exception) { failure(e); mutableStateFlow.update { it.copy(loading = false) } }
            finally { if (coroutineContext.isActive) startNextForegroundCheck() }
        }
    }

    private suspend fun reload(id: String? = null) {
        val data = repository.home(id)
        val pending = app.scanner.pending()
        mutableStateFlow.update { it.copy(loading = false, documents = data.documents, content = data.content,
            mirrorFailed = data.content?.document?.id in mirrorFailures, pending = pending,
            nodeGuide = it.nodeGuide?.takeIf { guide ->
                data.content?.document?.id == guide.documentId && data.content.nodes.size >= 4
            }) }
    }

    fun deleteDocument(id: String) = operation {
        repository.deleteDocument(id)
        mirrorFailures.remove(id)
        reload(stateFlow.value.content?.document?.id)
    }

    fun deleteNode(nodeId: String) {
        val id = stateFlow.value.content?.document?.id ?: return
        operation {
            val result = repository.deleteNode(id, nodeId)
            reload(id)
            saved(id, result)
        }
    }

    fun move(nodeId: String, beforeId: String?) {
        val id = stateFlow.value.content?.document?.id ?: return
        operation {
            val result = repository.move(id, nodeId, beforeId)
            reload(id)
            saved(id, result)
        }
    }

    fun mergeNodes(firstId: String, secondId: String) {
        val id = stateFlow.value.content?.document?.id ?: return
        operation {
            val result = repository.mergeNodes(id, firstId, secondId)
            reload(id)
            saved(id, result)
        }
    }

    fun moveImage(nodeId: String, imageId: String, direction: Int) {
        val id = stateFlow.value.content?.document?.id ?: return
        operation {
            val result = repository.moveImage(id, nodeId, imageId, direction)
            reload(id)
            saved(id, result)
        }
    }

    fun importImages(documentId: String, uris: List<String>, candidateUris: List<String> = emptyList()) = operation(waitForLoading = true) {
        val result = repository.importImages(documentId, uris, candidateUris)
        reload(documentId)
        mutableStateFlow.update { it.copy(message = "已添加 ${result.value} 个图片节点") }
        saved(documentId, result)
    }

    fun importCandidates(documentId: String, uris: List<String>) {
        val candidates = stateFlow.value.pending.associateBy { it.uri }
        val selected = uris.mapNotNull { candidates[it] }
        if (selected.size != uris.size) {
            showMessage("待选图片已变化，请重新选择")
            return
        }
        if (selected.isNotEmpty()) importImages(documentId, selected.map { it.uri }, selected.map { it.uri })
    }

    fun ignoreCandidates(uris: List<String>) = operation {
        app.scanner.ignore(uris)
        val pending = app.scanner.pending()
        mutableStateFlow.update { it.copy(pending = pending) }
    }

    fun export() {
        val id = stateFlow.value.content?.document?.id ?: return
        operation {
            val file = repository.export(id)
            mutableStateFlow.update { it.copy(exportedFile = file) }
        }
    }

    fun retryMirror() {
        val id = stateFlow.value.content?.document?.id ?: return
        operation {
            val success = repository.retryMirror(id)
            if (success) mirrorFailures.remove(id) else mirrorFailures.add(id)
            mutableStateFlow.update { it.copy(mirrorFailed = !success, message = if (success) "文档镜像已更新" else "已保存，文档镜像更新失败") }
        }
    }

    fun showMessage(message: String) { mutableStateFlow.update { it.copy(message = message) } }
    fun clearMessage() { mutableStateFlow.update { it.copy(message = null) } }
    fun consumedExport() { mutableStateFlow.update { it.copy(exportedFile = null) } }
    fun mirrorResult(documentId: String, failed: Boolean) {
        if (failed) mirrorFailures.add(documentId) else mirrorFailures.remove(documentId)
        mutableStateFlow.update { it.copy(mirrorFailed = it.content?.document?.id in mirrorFailures,
            message = if (failed) "已保存，文档镜像更新失败" else null) }
    }

    private fun saved(documentId: String, result: Saved<*>) {
        if (result.mirrorFailed) mirrorFailures.add(documentId) else mirrorFailures.remove(documentId)
        mutableStateFlow.update { it.copy(mirrorFailed = it.content?.document?.id in mirrorFailures,
            message = if (result.mirrorFailed) "已保存，文档镜像更新失败" else it.message) }
    }

    private fun operation(waitForLoading: Boolean = false, block: suspend () -> Unit) {
        if (stateFlow.value.busy) return
        if (stateFlow.value.loading && !waitForLoading) { showMessage("文档正在加载，请稍后重试"); return }
        val pendingReload = reloadJob
        val pendingScan = scanJob
        pendingScan?.cancel()
        mutableStateFlow.update { it.copy(busy = true, scanning = false) }
        viewModelScope.launch {
            try {
                withContext(NonCancellable) { pendingScan?.cancelAndJoin() }
                coroutineContext.ensureActive()
                if (waitForLoading) pendingReload?.join()
                block()
            } catch (e: CancellationException) { throw e } catch (e: Exception) { failure(e) }
            finally {
                mutableStateFlow.update { it.copy(busy = false) }
                if (coroutineContext.isActive) startNextForegroundCheck()
            }
        }
    }

    private fun failure(e: Exception) {
        logEx(throwable = e, message = "Document operation failed")
        mutableStateFlow.update { it.copy(message = e.message ?: "操作失败，请重试") }
    }

    class Factory(private val app: PicNoteApplication) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(PicNoteViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return PicNoteViewModel(app) as T
        }
    }
}
