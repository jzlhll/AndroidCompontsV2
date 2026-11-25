package com.allan.picnote.ui

import androidx.datastore.core.CorruptionException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.allan.picnote.R
import com.allan.picnote.data.CodeBlockContent
import com.allan.picnote.data.CodeLanguagePreferences
import com.allan.picnote.data.DefaultCodeLanguageOrder
import com.allan.picnote.data.PicNoteStorageException
import com.allan.picnote.data.parseCodeBlock
import com.allan.picnote.data.repositories.DocumentRepository
import com.allan.picnote.data.repositories.ImageDraft
import com.allan.picnote.data.repositories.Saved
import com.allan.picnote.logEx
import com.au.module_android.Globals
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds

data class EditorState(
    val loading: Boolean = false,
    val loaded: Boolean = true,
    val title: String = "",
    val kind: String = "body",
    val fixed: Boolean = false,
    val body: String = "",
    val code: String = "",
    val codeLanguage: String = "",
    val rawCodeMarkdown: Boolean = false,
    val images: List<ImageDraft> = emptyList(),
    val dirty: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val codeLanguageUsageError: String? = null,
    val saved: Saved<String>? = null,
    val savedImageCount: Int = 0,
)

/** 保存当前目的地的编辑草稿，配置变化时保留尚未提交的内容。 */
class EditorViewModel(
    private val repository: DocumentRepository,
    private val codeLanguagePreferences: CodeLanguagePreferences,
    val route: EditRoute,
) : ViewModel() {
    private val mutableStateFlow = MutableStateFlow(EditorState(loaded = route.nodeId == null, kind = route.kind))
    val stateFlow = mutableStateFlow.asStateFlow()
    val codeLanguagesFlow = codeLanguagePreferences.languageOrderFlow
        .retryWhen { e, attempt ->
            if (e !is IOException || e is CorruptionException) return@retryWhen false
            if (attempt == 0L) logEx(throwable = e, message = "Read code language weights failed")
            delay(if (attempt < 2) 500.milliseconds else 5000.milliseconds)
            true
        }
        .catch { e ->
            if (e is CancellationException) throw e
            logEx(throwable = e, message = "Read code language weights failed")
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), DefaultCodeLanguageOrder)
    private var originalDraft = mutableStateFlow.value
    private var originalCodeBlock: CodeBlockContent? = null
    private var originalCodeMarkdown: String? = null
    private var draftSession: String? = null

    init {
        if (route.nodeId != null) load()
    }

    fun load() {
        val documentId = route.documentId ?: return
        val nodeId = route.nodeId ?: return
        if (stateFlow.value.loaded || stateFlow.value.loading || stateFlow.value.busy) return
        mutableStateFlow.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val node = repository.editableNode(documentId, nodeId)
                val isCode = node.node.kind == "code"
                val codeBlock = if (isCode) withContext(Dispatchers.Default) { parseCodeBlock(node.node.bodyMarkdown) } else null
                originalCodeBlock = codeBlock
                originalCodeMarkdown = if (isCode) node.node.bodyMarkdown else null
                originalDraft = EditorState(title = node.node.title, kind = node.node.kind, fixed = node.fixed,
                    body = codeBlock?.description ?: if (isCode) "" else node.node.bodyMarkdown,
                    code = codeBlock?.code ?: if (isCode) node.node.bodyMarkdown else "",
                    codeLanguage = codeBlock?.language.orEmpty(),
                    rawCodeMarkdown = isCode && codeBlock == null,
                    images = node.images.map { ImageDraft(existing = it) }, savedImageCount = node.images.size)
                mutableStateFlow.value = originalDraft
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                logEx(throwable = e, message = "Load node draft failed")
                mutableStateFlow.update { it.copy(loading = false, loaded = false, error = failureMessage(e, R.string.error_load_node)) }
            }
        }
    }

    private fun updateDraft(transform: (EditorState) -> EditorState) {
        val current = stateFlow.value
        if (!current.loaded || current.loading || current.busy || current.saved != null) return
        val draft = transform(current)
        if (draft != current) mutableStateFlow.value = changed(draft.copy(error = null))
    }

    private fun changed(draft: EditorState): EditorState = draft.copy(
        dirty = draft.title.trim() != originalDraft.title.trim() || draft.body != originalDraft.body ||
            draft.code != originalDraft.code || draft.codeLanguage != originalDraft.codeLanguage ||
            draft.images.filterNot { it.pendingRemoval } != originalDraft.images,
    )

    fun setTitle(value: String) { updateDraft { it.copy(title = value) } }
    fun setBody(value: String) { updateDraft { it.copy(body = value) } }
    fun setCode(value: String) { updateDraft { it.copy(code = value) } }
    fun setCodeLanguage(value: String) {
        val current = stateFlow.value
        if (current.kind != "code" || current.rawCodeMarkdown || route.documentId == null || !current.loaded || current.loading ||
            current.busy || current.saved != null || value !in DefaultCodeLanguageOrder
        ) return
        mutableStateFlow.value = changed(current.copy(codeLanguage = value, error = null, codeLanguageUsageError = null))
        viewModelScope.launch {
            try {
                codeLanguagePreferences.recordSelection(value)
                ensureActive()
                mutableStateFlow.update { if (it.codeLanguage == value) it.copy(codeLanguageUsageError = null) else it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logEx(throwable = e, message = "Save code language weight failed")
                ensureActive()
                val message = Globals.app.getString(R.string.error_save_code_language_usage)
                mutableStateFlow.update { if (it.codeLanguage == value) it.copy(codeLanguageUsageError = message) else it }
            }
        }
    }
    fun imagePickerFailed(e: Exception) {
        logEx(throwable = e, message = "Launch editor image picker failed")
        mutableStateFlow.update { it.copy(error = Globals.app.getString(R.string.error_open_photo_picker)) }
    }

    fun dismissError() {
        mutableStateFlow.update {
            if (!it.loaded) it
            else if (it.error != null) it.copy(error = null)
            else it.copy(codeLanguageUsageError = null)
        }
    }

    fun addImages(uris: List<String>) { prepareImages(uris) }
    fun toggleImageRemoval(imageId: String) {
        updateDraft { draft ->
            draft.copy(images = draft.images.map { image ->
                if (image.id == imageId) image.copy(pendingRemoval = !image.pendingRemoval) else image
            })
        }
    }
    fun replaceImage(imageId: String, uri: String) {
        if (stateFlow.value.images.none { it.id == imageId }) return
        prepareImages(listOf(uri), imageId)
    }

    fun moveImage(imageId: String, direction: Int) {
        if (direction != -1 && direction != 1) return
        updateDraft { draft ->
            if (draft.kind != "body") return@updateDraft draft
            val index = draft.images.indexOfFirst { it.id == imageId }
            val target = index + direction
            if (index < 0 || target !in draft.images.indices) return@updateDraft draft
            val images = draft.images.toMutableList()
            images[index] = draft.images[target]
            images[target] = draft.images[index]
            draft.copy(images = images)
        }
    }

    private fun prepareImages(uris: List<String>, replacementId: String? = null) {
        val draft = stateFlow.value
        if (uris.isEmpty() || route.documentId == null || draft.kind != "body" || !draft.loaded || draft.loading || draft.busy || draft.saved != null) return
        mutableStateFlow.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val session = draftSession ?: repository.openDraftSession().also { opened ->
                    draftSession = opened
                    // 会话跟随目的地 ViewModel，旋转保留草稿，真正离开后清理文件。
                    viewModelScope.launch {
                        try { awaitCancellation() }
                        finally { withContext(NonCancellable) { repository.closeDraftSession(opened) } }
                    }
                }
                val prepared = repository.prepareImages(session, uris)
                val images = if (replacementId == null) draft.images + prepared.map { ImageDraft(prepared = it) }
                else draft.images.map { if (it.id == replacementId) ImageDraft(prepared = prepared.single()) else it }
                mutableStateFlow.value = changed(draft.copy(busy = false, images = images, error = null))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                logEx(throwable = e, message = "Prepare selected images failed")
                mutableStateFlow.update { it.copy(busy = false, error = failureMessage(e, R.string.error_prepare_images)) }
            }
        }
    }

    fun save() {
        val draft = stateFlow.value
        if (draft.busy || draft.loading || !draft.loaded || draft.saved != null || (route.nodeId != null && !draft.dirty && draft.kind != "code")) return
        mutableStateFlow.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val body = when {
                    draft.rawCodeMarkdown -> draft.code
                    draft.kind != "code" -> draft.body
                    else -> withContext(Dispatchers.Default) {
                        val code = try { formatCodeForSave(draft.code, draft.codeLanguage) }
                        catch (e: CancellationException) { throw e }
                        catch (e: StackOverflowError) {
                            logEx(throwable = e, message = "Format code exceeded tokenizer stack")
                            draft.code
                        }
                        catch (e: Exception) {
                            logEx(throwable = e, message = "Format code failed")
                            draft.code
                        }
                        val original = originalCodeBlock
                        val updated = (original ?: CodeBlockContent()).withLanguage(draft.codeLanguage)
                            .copy(description = draft.body, code = code)
                        if (updated == original) originalCodeMarkdown ?: updated.markdown() else updated.markdown()
                    }
                }
                val result = if (route.documentId == null) repository.create(draft.title, body)
                else repository.saveNode(route.documentId, route.nodeId, route.anchorId, draft.kind,
                    draft.title, body, draft.images.filterNot { it.pendingRemoval })
                mutableStateFlow.update { it.copy(busy = false, dirty = false, saved = result) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                logEx(throwable = e, message = "Save draft failed")
                mutableStateFlow.update { it.copy(busy = false, error = failureMessage(e, R.string.error_save)) }
            }
        }
    }

    fun deleteNode() {
        val documentId = route.documentId ?: return
        val nodeId = route.nodeId ?: return
        val draft = stateFlow.value
        if (!draft.loaded || draft.fixed || draft.loading || draft.busy || draft.saved != null) return
        mutableStateFlow.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val result = repository.deleteNode(documentId, nodeId)
                mutableStateFlow.update { it.copy(busy = false, dirty = false, saved = Saved(nodeId, result.mirrorFailed)) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                logEx(throwable = e, message = "Delete node failed")
                mutableStateFlow.update { it.copy(busy = false, error = failureMessage(e, R.string.error_delete)) }
            }
        }
    }

    private fun failureMessage(error: Exception, fallback: Int): String =
        Globals.app.getString((error as? PicNoteStorageException)?.messageRes ?: fallback)

    class Factory(
        private val repository: DocumentRepository,
        private val codeLanguagePreferences: CodeLanguagePreferences,
        private val route: EditRoute,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(EditorViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return EditorViewModel(repository, codeLanguagePreferences, route) as T
        }
    }
}
