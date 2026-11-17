package com.allan.picnote.ui

import android.os.SystemClock
import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.allan.picnote.PicNoteApplication
import com.allan.picnote.R
import com.allan.picnote.logEx
import com.allan.picnote.logd
import com.allan.picnote.data.PermissionHistory
import com.allan.picnote.data.WelcomeDocumentId
import com.allan.picnote.data.NodeGuideKind
import com.allan.picnote.data.ImportMode
import com.allan.picnote.data.entity.*
import com.allan.picnote.data.repositories.DocumentRepository
import com.allan.picnote.data.repositories.Saved
import com.allan.picnote.media.*
import com.au.module_android.utils.withIOThread
import com.au.module_android.Globals
import com.au.module_android.utilsmedia.saveFileToPublicDirectory
import com.au.module_androidui.toast.ToastUtil
import com.au.module_androiduiex.styles.ComposeTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
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

data class CandidateImportRequest(val selected: List<PendingMediaEntity>, val batch: List<PendingMediaEntity>, val mode: ImportMode)
data class CandidateImportConfirmation(val documentId: String, val request: CandidateImportRequest)

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
    val importMode: ImportMode? = null,
    val importModeSaving: Boolean = false,
    val candidateImportConfirmation: CandidateImportConfirmation? = null,
    val mediaDeleteRequest: IntentSender? = null,
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
    private var preparedCandidateImport: CandidateImportRequest? = null
    private var mediaDeleteResult: CompletableDeferred<Boolean>? = null

    init {
        viewModelScope.launch {
            try {
                val mode = app.importModePreferences.readMode()
                mutableStateFlow.update { it.copy(importMode = mode) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                logEx(throwable = e, message = "Read import mode failed")
                mutableStateFlow.update { it.copy(importMode = ImportMode.AddOnly, message = Globals.app.getString(R.string.error_read_import_mode)) }
            }
        }
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

    fun setImportMode(mode: ImportMode) {
        val state = stateFlow.value
        val previous = state.importMode ?: return
        if (state.busy || state.importModeSaving || previous == mode) return
        if (photoAccess(app) != PhotoAccess.Full) {
            showMessage(Globals.app.getString(R.string.error_full_access_import_mode))
            return
        }
        mutableStateFlow.update { it.copy(importMode = mode, importModeSaving = true) }
        viewModelScope.launch {
            try {
                app.importModePreferences.saveMode(mode)
                mutableStateFlow.update { it.copy(importModeSaving = false) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                logEx(throwable = e, message = "Save import mode failed")
                mutableStateFlow.update { it.copy(importMode = previous, importModeSaving = false,
                    message = Globals.app.getString(R.string.error_save_import_mode)) }
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

    fun duplicateDocument(id: String) = operation {
        val result = repository.duplicate(id)
        reload(result.value)
        ToastUtil.toastOnTop("已复制文档")
        saved(result.value, result)
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

    fun importImages(documentId: String, uris: List<String>) = operation(waitForLoading = true) {
        val result = repository.importImages(documentId, uris, emptyList())
        reload(documentId)
        ToastUtil.toastOnTop("已添加 ${result.value} 个图片节点")
        saved(documentId, result)
    }

    fun prepareCandidateImport(uris: List<String>): Boolean {
        val state = stateFlow.value
        if (state.busy || state.loading || state.importModeSaving || state.candidateImportConfirmation != null) return false
        if (photoAccess(app) != PhotoAccess.Full) {
            showMessage(Globals.app.getString(R.string.error_full_access_import_mode))
            return false
        }
        val mode = state.importMode ?: run {
            showMessage(Globals.app.getString(R.string.error_import_mode_loading))
            return false
        }
        val batch = state.pending.toList()
        val candidates = batch.associateBy { it.uri }
        val selected = uris.distinct().mapNotNull { candidates[it] }
        if (selected.isEmpty() || selected.size != uris.size) {
            showMessage(Globals.app.getString(R.string.pending_images_changed))
            return false
        }
        preparedCandidateImport = CandidateImportRequest(selected, batch, mode)
        return true
    }

    fun cancelPreparedCandidateImport() { preparedCandidateImport = null }

    fun importCandidates(documentId: String) {
        val request = preparedCandidateImport
        preparedCandidateImport = null
        if (request == null) {
            showMessage(Globals.app.getString(R.string.pending_images_changed))
            return
        }
        val confirmation = CandidateImportConfirmation(documentId, request)
        if (request.mode == ImportMode.DeleteBatch) {
            mutableStateFlow.update { it.copy(candidateImportConfirmation = confirmation) }
        } else performCandidateImport(confirmation)
    }

    fun dismissCandidateImportConfirmation() {
        mutableStateFlow.update { it.copy(candidateImportConfirmation = null) }
    }

    fun confirmCandidateImport() {
        val confirmation = stateFlow.value.candidateImportConfirmation ?: return
        mutableStateFlow.update { it.copy(candidateImportConfirmation = null) }
        performCandidateImport(confirmation)
    }

    private fun performCandidateImport(confirmation: CandidateImportConfirmation) = operation(waitForLoading = true) {
        val request = confirmation.request
        val current = app.scanner.pending().associateBy { it.uri }
        check(request.selected.all { current[it.uri] == it }) { Globals.app.getString(R.string.pending_images_changed) }
        val deletion = if (request.mode == ImportMode.AddOnly) null else app.scanner.deletionBatch(
            if (request.mode == ImportMode.DeleteBatch) request.batch else request.selected)
        val selected = request.selected.map { it.uri }
        val result = repository.importImages(confirmation.documentId, selected, selected)
        app.scanner.ignoreBatch(request.batch.map { it.uri })
        reload(confirmation.documentId)
        ToastUtil.toastOnTop(Globals.app.getString(R.string.imported_image_nodes, result.value))
        saved(confirmation.documentId, result)
        if (deletion != null) {
            try {
                val completed = app.mediaDeletion.delete(deletion, ::requestMediaDeletion)
                showMessage(Globals.app.getString(if (completed) R.string.import_saved_delete_completed else R.string.import_saved_delete_cancelled))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                logEx(throwable = e, message = "Delete imported system images failed")
                showMessage(Globals.app.getString(R.string.import_saved_delete_failed))
            }
        }
    }

    private suspend fun requestMediaDeletion(sender: IntentSender): Boolean {
        val result = CompletableDeferred<Boolean>()
        check(mediaDeleteResult == null)
        mediaDeleteResult = result
        mutableStateFlow.update { it.copy(mediaDeleteRequest = sender) }
        try {
            return result.await()
        } finally {
            mediaDeleteResult = null
            mutableStateFlow.update { it.copy(mediaDeleteRequest = null) }
        }
    }

    fun consumeMediaDeleteRequest(): IntentSender? {
        val sender = stateFlow.value.mediaDeleteRequest ?: return null
        mutableStateFlow.update { it.copy(mediaDeleteRequest = null) }
        return sender
    }

    fun completeMediaDeleteRequest(approved: Boolean) { mediaDeleteResult?.complete(approved) }

    fun failMediaDeleteRequest(error: Exception) { mediaDeleteResult?.completeExceptionally(error) }

    fun ignorePending() = operation {
        app.scanner.ignoreAll()
        val pending = app.scanner.pending()
        mutableStateFlow.update { it.copy(pending = pending) }
    }

    fun exportFull(id: String? = stateFlow.value.content?.document?.id) = exportWith(id, repository::exportFull)

    fun exportWeb(id: String? = stateFlow.value.content?.document?.id) = exportWith(id, repository::exportWeb)

    fun downloadImage(imageId: String) {
        val image = stateFlow.value.content?.nodes?.flatMap { it.images }?.firstOrNull { it.id == imageId } ?: return
        operation {
            try {
                val saved = withIOThread {
                    saveFileToPublicDirectory(app, repository.imageFile(image.documentId, image.fileName),
                        deleteOldFile = false, path = "Bambootes")
                }
                checkNotNull(saved)
                showMessage(Globals.app.getString(R.string.image_downloaded))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                logEx(throwable = e, message = "Download image failed imageId=$imageId")
                showMessage(Globals.app.getString(R.string.error_download_image))
            }
        }
    }

    private fun exportWith(id: String?, produce: suspend (String) -> File) {
        if (id == null) return
        operation {
            val file = produce(id)
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
