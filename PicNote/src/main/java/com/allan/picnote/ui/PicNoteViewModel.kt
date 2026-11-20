package com.allan.picnote.ui

import android.os.SystemClock
import android.content.IntentSender
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.allan.picnote.PicNoteApplication
import com.allan.picnote.R
import com.allan.picnote.logEx
import com.allan.picnote.logd
import com.allan.picnote.data.CodeWrapSettings
import com.allan.picnote.data.PermissionHistory
import com.allan.picnote.data.WelcomeDocumentId
import com.allan.picnote.data.NodeGuideKind
import com.allan.picnote.data.PicNoteStorageException
import com.allan.picnote.data.ImportMode
import com.allan.picnote.data.entity.*
import com.allan.picnote.data.repositories.DocumentRepository
import com.allan.picnote.data.repositories.Saved
import com.allan.picnote.media.*
import com.au.module_android.utils.withIOThread
import com.au.module_android.Globals
import com.au.module_android.utilsmedia.saveFileToPublicDirectory
import com.au.module_androidui.toast.MyToastUtil
import com.au.module_androiduiex.styles.ComposeTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import java.io.File

data class CandidateImportRequest(val selected: List<PendingMediaEntity>, val batch: List<PendingMediaEntity>, val mode: ImportMode)
data class CandidateImportConfirmation(val documentId: String, val request: CandidateImportRequest)
private data class PreparedCandidateImport(val entryId: String, val request: CandidateImportRequest)
data class ExportShareRequest(val file: File, val openRequested: Boolean = true)

data class HomeState(
    val loading: Boolean = true,
    val documents: List<DocumentSummary> = emptyList(),
    val content: DocumentContent? = null,
    val access: PhotoAccess = PhotoAccess.Denied,
    val scanning: Boolean = false,
    val scanError: String? = null,
    val pending: List<PendingMediaEntity> = emptyList(),
    val candidateSelection: Set<PendingMediaEntity> = emptySet(),
    val mergeSelection: List<String>? = null,
    val busy: Boolean = false,
    val mirrorFailed: Boolean = false,
    val exportShareRequest: ExportShareRequest? = null,
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
    val nodeGuideSaveFailed: Boolean = false,
    val importMode: ImportMode? = null,
    val importModeSaving: Boolean = false,
    val codeWrapSettings: CodeWrapSettings? = null,
    val candidateImportConfirmation: CandidateImportConfirmation? = null,
    val mediaDeleteRequest: IntentSender? = null,
)

/** 连接首页与本地存储，写操作在执行前等待已取消的前台检查退出。 */
class PicNoteViewModel(private val app: PicNoteApplication) : ViewModel() {
    val repository: DocumentRepository = app.documents
    private val mutableStateFlow = MutableStateFlow(HomeState(access = photoAccess(app)))
    val stateFlow = mutableStateFlow.asStateFlow()
    private var scanJob: Job? = null
    private var reloadJob: Job? = null
    private var documentListRefreshJob: Job? = null
    private val messageChannel = Channel<String>(Channel.UNLIMITED)
    val messagesFlow = messageChannel.receiveAsFlow()
    private val codeWrapSettingsMutex = Mutex()
    private var pendingForegroundCheck: Int? = null
    private val mirrorFailures = mutableSetOf<String>()
    private var preparedCandidateImport: PreparedCandidateImport? = null
    private var mediaDeleteResult: CompletableDeferred<Boolean>? = null

    init {
        viewModelScope.launch {
            try {
                val settings = app.codeWrapPreferences.read()
                mutableStateFlow.update { it.copy(codeWrapSettings = settings) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                logEx(throwable = e, message = "Read code wrap settings failed")
                mutableStateFlow.update { it.copy(codeWrapSettings = CodeWrapSettings()) }
                showMessage(R.string.error_read_code_wrap_settings)
            }
        }
        viewModelScope.launch {
            try {
                val mode = app.importModePreferences.readMode()
                mutableStateFlow.update { it.copy(importMode = mode) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                logEx(throwable = e, message = "Read import mode failed")
                mutableStateFlow.update { it.copy(importMode = ImportMode.AddOnly) }
                showMessage(R.string.error_read_import_mode)
            }
        }
        viewModelScope.launch {
            try {
                val theme = app.appearancePreferences.readTheme()
                mutableStateFlow.update { it.copy(theme = theme) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                logEx(throwable = e, message = "Read appearance failed")
                mutableStateFlow.update { it.copy(theme = ComposeTheme.Sage) }
                showMessage(R.string.error_read_appearance)
            }
        }
        viewModelScope.launch {
            try {
                val history = app.permissionPreferences.read()
                mutableStateFlow.update { it.copy(permissionHistory = history, permissionHistoryLoading = false) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                logEx(throwable = e, message = "Read image permission history failed")
                mutableStateFlow.update { it.copy(permissionHistoryLoading = false, permissionHistoryFailed = true) }
                showMessage(R.string.error_read_permission_history)
            }
        }
        reloadJob = viewModelScope.launch {
            try {
                try {
                    repository.initializeWelcome()?.let { if (it.mirrorFailed) mirrorFailures.add(it.value) }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { failure(e, R.string.error_initialize_welcome) }
                mirrorFailures.addAll(app.awaitStartupCleanup().mirrorFailures)
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
        viewModelScope.launch { checkNodeGuideOnStartup() }
    }

    private suspend fun checkNodeGuideOnStartup() {
        if (app.startupNodeGuideResolved) return
        try {
            val kind = app.nodeGuidePreferences.guideForStartup()
            if (kind == null) {
                app.startupNodeGuideResolved = true
                return
            }
            reloadJob?.join()
            if (app.startupNodeGuideResolved || stateFlow.value.loading) return
            val opened = stateFlow.value.content
            if (opened == null || opened.document.id == WelcomeDocumentId || opened.nodes.size < 4) {
                app.startupNodeGuideResolved = true
                return
            }
            mutableStateFlow.update { it.copy(nodeGuide = NodeGuide(opened.document.id, kind)) }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { logEx(throwable = e, message = "Read node guide failed") }
    }

    fun dismissNodeGuide() {
        if (stateFlow.value.nodeGuideSaving) return
        app.startupNodeGuideResolved = true
        mutableStateFlow.update { it.copy(nodeGuide = null, nodeGuideSaveFailed = false) }
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
        mutableStateFlow.update { it.copy(nodeGuideSaving = true, nodeGuideSaveFailed = false) }
        viewModelScope.launch {
            try {
                app.nodeGuidePreferences.acknowledgeGuide(guide.kind)
                app.startupNodeGuideResolved = true
                mutableStateFlow.update { it.copy(nodeGuide = null) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                logEx(throwable = e, message = "Save node guide acknowledgement failed kind=${guide.kind}")
                mutableStateFlow.update { it.copy(nodeGuideSaveFailed = true) }
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
                mutableStateFlow.update { it.copy(theme = previous, themeSaving = false) }
                showMessage(R.string.error_save_appearance)
            }
        }
    }

    fun setImportMode(mode: ImportMode) {
        val state = stateFlow.value
        val previous = state.importMode ?: return
        if (state.busy || state.importModeSaving || previous == mode) return
        if (photoAccess(app) != PhotoAccess.Full) {
            showMessage(R.string.error_full_access_import_mode)
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
                mutableStateFlow.update { it.copy(importMode = previous, importModeSaving = false) }
                showMessage(R.string.error_save_import_mode)
            }
        }
    }

    fun toggleNodeCardCodeWrap() = updateCodeWrapSettings {
        it.copy(nodeCard = app.codeWrapPreferences.toggleNodeCard())
    }

    fun toggleDocumentCodeWrap() = updateCodeWrapSettings {
        it.copy(document = app.codeWrapPreferences.toggleDocument())
    }

    private fun updateCodeWrapSettings(update: suspend (CodeWrapSettings) -> CodeWrapSettings) {
        viewModelScope.launch {
            codeWrapSettingsMutex.withLock {
                val settings = stateFlow.value.codeWrapSettings ?: return@withLock
                try {
                    val updated = update(settings)
                    mutableStateFlow.update { it.copy(codeWrapSettings = updated) }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    logEx(throwable = e, message = "Save code wrap settings failed")
                    showMessage(R.string.error_save_code_wrap_settings)
                }
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
                mutableStateFlow.update { it.copy(permissionPromptSaving = false, permissionHistoryFailed = true) }
                showMessage(R.string.error_save_permission_explanation)
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
                mutableStateFlow.update { it.copy(permissionPromptSaving = false, permissionHistoryFailed = true) }
                showMessage(R.string.error_save_permission_request)
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
        mutableStateFlow.update { it.copy(access = access, pending = if (access == PhotoAccess.Full) it.pending else emptyList()) }
    }

    fun checkOnForeground(ordinal: Int) {
        pendingForegroundCheck = ordinal
        logd("event=image_check_queued startupId=${app.startupId} pid=${app.processId} foregroundOrdinal=$ordinal busy=${stateFlow.value.busy} loading=${stateFlow.value.loading}")
        startNextForegroundCheck()
    }

    private fun startNextForegroundCheck() {
        if (stateFlow.value.busy || stateFlow.value.loading || scanJob != null) return
        val ordinal = pendingForegroundCheck ?: return
        pendingForegroundCheck = null
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
                    added = app.scanner.scan()
                    val openedContent = stateFlow.value.content
                    val refreshedContent = openedContent?.let { repository.contentIfExists(it.document.id) }
                    val pending = app.scanner.pending()
                    status = "success"
                    logd("event=image_check_result $fields added=$added pending=${pending.size}")
                    mutableStateFlow.update {
                        val content = if (it.content === openedContent && refreshedContent != null) refreshedContent else it.content
                        it.copy(pending = pending, content = content,
                            candidateSelection = pendingSelection(it, pending, content))
                    }
                } else {
                    app.scanner.permissionLost()
                    status = "skipped"
                }
            } catch (e: CancellationException) {
                status = "cancelled"
                throw e
            } catch (e: Exception) {
                logEx(throwable = e, message = "Image check failed $fields")
                val currentAccess = photoAccess(app)
                var error = (e as? PicNoteStorageException)?.messageRes ?: R.string.error_scan_images
                if (access == PhotoAccess.Full && currentAccess != PhotoAccess.Full) {
                    try {
                        app.scanner.permissionLost()
                    } catch (failure: CancellationException) { throw failure }
                    catch (failure: Exception) {
                        logEx(throwable = failure, message = "Invalidate media baseline failed")
                        error = R.string.error_save_media_baseline
                    }
                }
                mutableStateFlow.update { it.copy(access = currentAccess, scanError = Globals.app.getString(error),
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
        val pendingListRefresh = documentListRefreshJob
        previous?.cancel()
        pendingScan?.cancel()
        pendingListRefresh?.cancel()
        mutableStateFlow.update { it.copy(loading = true, scanning = false) }
        reloadJob = viewModelScope.launch {
            withContext(NonCancellable) {
                previous?.cancelAndJoin()
                pendingScan?.cancelAndJoin()
                pendingListRefresh?.cancelAndJoin()
            }
            coroutineContext.ensureActive()
            try { reload(id) } catch (e: CancellationException) { throw e }
            catch (e: Exception) { failure(e); mutableStateFlow.update { it.copy(loading = false) } }
            finally { if (coroutineContext.isActive) startNextForegroundCheck() }
        }
    }

    fun refreshDocumentList() {
        if (stateFlow.value.busy) return
        val previous = documentListRefreshJob
        val pendingReload = reloadJob
        previous?.cancel()
        documentListRefreshJob = viewModelScope.launch {
            try {
                withContext(NonCancellable) { previous?.cancelAndJoin() }
                pendingReload?.join()
                val documents = repository.documentSummaries()
                coroutineContext.ensureActive()
                mutableStateFlow.update { it.copy(documents = documents) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { failure(e) }
        }
    }

    private suspend fun reload(id: String? = null) {
        val data = repository.home(id)
        var pendingError: String? = null
        val availableCandidates = try {
            app.scanner.pending()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            logEx(throwable = e, message = "Load suggested images failed")
            pendingError = Globals.app.getString((e as? PicNoteStorageException)?.messageRes ?: R.string.error_scan_images)
            stateFlow.value.pending
        }
        val access = photoAccess(app)
        val pending = if (access == PhotoAccess.Full) availableCandidates else emptyList()
        mutableStateFlow.update {
            val guide = it.nodeGuide?.takeIf { guide ->
                data.content?.document?.id == guide.documentId && data.content.nodes.size >= 4
            }
            if (it.nodeGuide != null && guide == null) app.startupNodeGuideResolved = true
            val sameDocument = it.content?.document?.id == data.content?.document?.id
            it.copy(loading = false, documents = data.documents, content = data.content, nodeGuide = guide, access = access,
                mirrorFailed = data.content?.document?.id in mirrorFailures, pending = pending,
                mergeSelection = if (sameDocument) it.mergeSelection else null,
                scanError = pendingError ?: it.scanError,
                candidateSelection = pendingSelection(it, pending, data.content))
        }
    }

    private fun pendingSelection(
        state: HomeState,
        pending: List<PendingMediaEntity>,
        content: DocumentContent?,
    ): Set<PendingMediaEntity> {
        if (state.candidateSelection.isEmpty() || state.content?.document?.id != content?.document?.id) return emptySet()
        val available = pending.toHashSet()
        val imported = content?.importedSourceUris.orEmpty()
        return state.candidateSelection.filterTo(linkedSetOf()) { it in available && it.uri !in imported }
    }

    fun deleteDocument(id: String) = operation {
        repository.deleteDocument(id)
        mirrorFailures.remove(id)
        reload(stateFlow.value.content?.document?.id)
    }

    fun duplicateDocument(id: String) = operation {
        val result = repository.duplicate(id)
        reload(result.value)
        MyToastUtil.toastOnBottom(Globals.app.getString(R.string.document_duplicated))
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
            val result = repository.move(id, nodeId, beforeId) ?: return@operation
            reload(id)
            saved(id, result)
        }
    }

    fun setMergeSelection(selection: List<String>?) {
        mutableStateFlow.update { it.copy(mergeSelection = selection) }
    }

    fun mergeNodes(nodeIds: List<String>) {
        val id = stateFlow.value.content?.document?.id ?: return
        operation {
            val result = repository.mergeNodes(id, nodeIds)
            setMergeSelection(null)
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
        val result = repository.importImages(documentId, uris)
        reload(documentId)
        showImportedImages(result)
        saved(documentId, result)
    }

    fun toggleCandidate(documentId: String?, candidate: PendingMediaEntity) {
        val state = stateFlow.value
        if (state.loading || state.busy || state.content?.document?.id != documentId || candidate !in state.pending ||
            candidate.uri in state.content?.importedSourceUris.orEmpty()
        ) return
        val selection = state.candidateSelection
        mutableStateFlow.update {
            it.copy(candidateSelection = if (candidate in selection) selection - candidate else selection + candidate)
        }
    }

    fun prepareCandidateImport(documentId: String?, selected: List<PendingMediaEntity>): CandidateImportRequest? {
        val state = stateFlow.value
        if (state.busy || state.loading || state.importModeSaving || state.candidateImportConfirmation != null) return null
        if (photoAccess(app) != PhotoAccess.Full) {
            showMessage(R.string.error_full_access_import_mode)
            return null
        }
        val mode = state.importMode ?: run {
            showMessage(R.string.error_import_mode_loading)
            return null
        }
        val batch = state.pending.toList()
        val candidates = batch.toHashSet()
        val imported = state.content?.importedSourceUris.orEmpty()
        if (state.content?.document?.id != documentId || selected.isEmpty() || selected.size != selected.toSet().size ||
            selected.any { it !in candidates || it.uri in imported }
        ) {
            showMessage(R.string.pending_images_changed)
            return null
        }
        return CandidateImportRequest(selected.toList(), batch, mode)
    }

    fun deferCandidateImport(entryId: String, request: CandidateImportRequest) {
        preparedCandidateImport = PreparedCandidateImport(entryId, request)
    }

    fun cancelPreparedCandidateImport(entryId: String) {
        if (preparedCandidateImport?.entryId == entryId) preparedCandidateImport = null
    }

    fun resumeCandidateImport(documentId: String, entryId: String) {
        val prepared = preparedCandidateImport
        if (prepared == null || prepared.entryId != entryId) {
            showMessage(R.string.candidate_selection_expired)
            return
        }
        preparedCandidateImport = null
        importCandidates(documentId, prepared.request)
    }

    fun importCandidates(documentId: String, request: CandidateImportRequest) {
        val confirmation = CandidateImportConfirmation(documentId, request)
        if (request.mode == ImportMode.DeleteBatch) {
            mutableStateFlow.update { it.copy(candidateImportConfirmation = confirmation) }
        } else performCandidateImport(confirmation)
    }

    fun dismissCandidateImportConfirmation() {
        mutableStateFlow.update { it.copy(candidateImportConfirmation = null) }
    }

    fun confirmCandidateImport() {
        val state = stateFlow.value
        val confirmation = state.candidateImportConfirmation ?: return
        if (state.busy || state.loading) return
        mutableStateFlow.update { it.copy(candidateImportConfirmation = null) }
        performCandidateImport(confirmation)
    }

    private fun performCandidateImport(confirmation: CandidateImportConfirmation) = operation(waitForLoading = true) {
        val request = confirmation.request
        if (photoAccess(app) != PhotoAccess.Full) {
            showMessage(R.string.error_full_access_import_mode)
            return@operation
        }
        val current = app.scanner.pending().associateBy { it.uri }
        if (request.selected.any { current[it.uri] != it }) {
            showMessage(R.string.pending_images_changed)
            return@operation
        }
        val selected = request.selected.map { it.uri }
        val consumed = if (request.mode == ImportMode.DeleteBatch) request.batch else request.selected
        val result = repository.importImages(confirmation.documentId, selected, request.selected, batchToConsume = consumed)
        reload(confirmation.documentId)
        showImportedImages(result)
        saved(confirmation.documentId, result)
        if (request.mode != ImportMode.AddOnly) {
            try {
                // 使用点击时冻结的图片和卷版本，保存完成后才校验并发起系统删除。
                val deletion = MediaDeletionBatch(consumed)
                val completed = app.mediaDeletion.delete(deletion, ::requestMediaDeletion)
                showMessage(if (completed) R.string.import_saved_delete_completed else R.string.import_saved_delete_cancelled)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                logEx(throwable = e, message = "Delete imported system images failed")
                if (e is PicNoteStorageException) {
                    showMessage(Globals.app.getString(R.string.import_saved_delete_failed_with_reason, Globals.app.getString(e.messageRes)))
                } else {
                    showMessage(R.string.import_saved_delete_failed)
                }
            }
        }
    }

    private fun showImportedImages(result: Saved<Int>) {
        val message = if (result.skippedImages > 0) {
            Globals.app.getString(R.string.imported_image_nodes_with_duplicates, result.value, result.skippedImages)
        } else {
            Globals.app.getString(R.string.imported_image_nodes, result.value)
        }
        MyToastUtil.toastOnBottom(message)
    }

    fun showSkippedImages(count: Int) {
        if (count > 0) showMessage(Globals.app.getString(R.string.skipped_duplicate_images, count))
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
        mutableStateFlow.update { it.copy(pending = pending, candidateSelection = emptySet()) }
    }

    fun exportFull(id: String? = stateFlow.value.content?.document?.id) = exportWith(id, repository::exportFull)

    fun exportWeb(id: String? = stateFlow.value.content?.document?.id) = exportWith(id, repository::exportWeb)

    fun clearCache() = operation {
        try {
            val result = repository.cleaner.clean(clearAllCache = true)
            mirrorFailures.clear()
            mirrorFailures.addAll(result.mirrorFailures)
            mutableStateFlow.update { it.copy(mirrorFailed = it.content?.document?.id in mirrorFailures,
                exportShareRequest = null) }
            showMessage(if (result.failedTasks.isEmpty()) R.string.cache_cleared else R.string.cache_clear_incomplete)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            logEx(throwable = e, message = "Clear cache failed")
            showMessage(R.string.error_clear_cache)
        }
    }

    fun downloadImage(imageId: String) {
        val image = stateFlow.value.content?.nodes?.flatMap { it.images }?.firstOrNull { it.id == imageId } ?: return
        operation {
            try {
                val saved = withIOThread {
                    saveFileToPublicDirectory(app, repository.imageFile(image.documentId, image.fileName),
                        deleteOldFile = false, path = "Bambootes")
                }
                checkNotNull(saved)
                showMessage(R.string.image_downloaded)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                logEx(throwable = e, message = "Download image failed imageId=$imageId")
                showMessage(R.string.error_download_image)
            }
        }
    }

    private fun exportWith(id: String?, produce: suspend (String) -> File) {
        if (id == null) return
        if (stateFlow.value.busy) {
            showMessage(R.string.document_operation_in_progress)
            return
        }
        operation {
            val started = SystemClock.elapsedRealtime()
            var status = "failed"
            var bytes = 0L
            try {
                val file = produce(id)
                bytes = withIOThread { file.length() }
                mutableStateFlow.update { it.copy(exportShareRequest = ExportShareRequest(file)) }
                status = "success"
            } catch (e: CancellationException) {
                status = "cancelled"
                throw e
            } catch (e: Exception) {
                failure(e, R.string.error_export_document)
            } finally {
                logd("event=document_export status=$status elapsedMs=${SystemClock.elapsedRealtime() - started} bytes=$bytes")
            }
        }
    }

    fun retryExportShare() {
        mutableStateFlow.update {
            val request = it.exportShareRequest
            if (request?.openRequested == false) it.copy(exportShareRequest = request.copy(openRequested = true)) else it
        }
    }

    fun completeExportShare(file: File, opened: Boolean) {
        if (stateFlow.value.exportShareRequest?.file != file) return
        mutableStateFlow.update { it.copy(exportShareRequest = if (opened) null else ExportShareRequest(file, openRequested = false)) }
    }

    fun retryMirror() {
        val id = stateFlow.value.content?.document?.id ?: return
        operation {
            val success = repository.retryMirror(id)
            if (success) mirrorFailures.remove(id) else mirrorFailures.add(id)
            mutableStateFlow.update { it.copy(mirrorFailed = !success) }
            showMessage(if (success) R.string.mirror_updated else R.string.mirror_failed)
        }
    }

    fun showMessage(message: String) { messageChannel.trySend(message) }
    private fun showMessage(@StringRes messageRes: Int) = showMessage(Globals.app.getString(messageRes))

    fun mirrorResult(documentId: String, failed: Boolean, notifyOnFailure: Boolean = true) {
        if (failed) mirrorFailures.add(documentId) else mirrorFailures.remove(documentId)
        mutableStateFlow.update { it.copy(mirrorFailed = it.content?.document?.id in mirrorFailures) }
        if (failed && notifyOnFailure) showMessage(R.string.mirror_failed)
    }

    private fun saved(documentId: String, result: Saved<*>) {
        mirrorResult(documentId, result.mirrorFailed)
    }

    private fun operation(waitForLoading: Boolean = false, block: suspend () -> Unit) {
        if (stateFlow.value.busy) return
        if (stateFlow.value.loading && !waitForLoading) { showMessage(R.string.document_loading); return }
        val pendingReload = reloadJob
        val pendingScan = scanJob
        val pendingListRefresh = documentListRefreshJob
        pendingScan?.cancel()
        pendingListRefresh?.cancel()
        mutableStateFlow.update { it.copy(busy = true, scanning = false) }
        viewModelScope.launch {
            try {
                withContext(NonCancellable) {
                    pendingScan?.cancelAndJoin()
                    pendingListRefresh?.cancelAndJoin()
                }
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

    private fun failure(e: Exception, @StringRes defaultMessage: Int = R.string.error_operation) {
        logEx(throwable = e, message = "Document operation failed")
        when {
            e !is PicNoteStorageException -> showMessage(defaultMessage)
            e.messageArguments.isEmpty() -> showMessage(e.messageRes)
            else -> showMessage(ContextCompat.getContextForLanguage(Globals.app)
                .getString(e.messageRes, *e.messageArguments.toTypedArray()))
        }
    }

    class Factory(private val app: PicNoteApplication) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(PicNoteViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return PicNoteViewModel(app) as T
        }
    }
}
