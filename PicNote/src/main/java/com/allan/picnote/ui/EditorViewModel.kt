package com.allan.picnote.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.allan.picnote.data.repositories.DocumentRepository
import com.allan.picnote.data.repositories.ImageDraft
import com.allan.picnote.logEx
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorState(
    val loading: Boolean = false,
    val loaded: Boolean = true,
    val title: String = "",
    val kind: String = "body",
    val fixed: Boolean = false,
    val body: String = "",
    val images: List<ImageDraft> = emptyList(),
    val dirty: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val savedId: String? = null,
    val mirrorFailed: Boolean = false,
    val savedImageCount: Int = 0,
)

/** 保存当前目的地的编辑草稿，配置变化时保留尚未提交的内容。 */
class EditorViewModel(private val repository: DocumentRepository, val route: EditRoute) : ViewModel() {
    private val mutableStateFlow = MutableStateFlow(EditorState(loaded = route.nodeId == null, kind = route.kind))
    val stateFlow = mutableStateFlow.asStateFlow()
    private var originalDraft = mutableStateFlow.value
    private val draftSession = repository.openDraftSession()

    init {
        // 会话跟随目的地 ViewModel，旋转保留草稿，真正离开后再清理文件。
        viewModelScope.launch {
            try { awaitCancellation() }
            finally { withContext(NonCancellable) { repository.closeDraftSession(draftSession) } }
        }
        if (route.nodeId != null) load()
    }

    fun load() {
        val documentId = route.documentId ?: return
        val nodeId = route.nodeId ?: return
        if (stateFlow.value.loaded || stateFlow.value.loading || stateFlow.value.busy) return
        mutableStateFlow.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val nodes = repository.content(documentId).nodes
                val index = nodes.indexOfFirst { it.node.id == nodeId }
                val node = nodes.getOrNull(index) ?: error("节点已删除")
                originalDraft = EditorState(title = node.node.title, kind = node.node.kind, fixed = index < 2, body = node.node.bodyMarkdown,
                    images = node.images.map { ImageDraft(existing = it) }, savedImageCount = node.images.size)
                mutableStateFlow.value = originalDraft
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                logEx(throwable = e, message = "Load node draft failed")
                mutableStateFlow.update { it.copy(loading = false, loaded = false, error = e.message ?: "无法打开节点") }
            }
        }
    }

    private fun updateDraft(transform: (EditorState) -> EditorState) {
        val current = stateFlow.value
        if (!current.loaded || current.loading || current.busy || current.savedId != null) return
        val draft = transform(current)
        mutableStateFlow.value = changed(draft)
    }

    private fun changed(draft: EditorState): EditorState = draft.copy(
        dirty = draft.title.trim() != originalDraft.title.trim() || draft.body != originalDraft.body ||
            draft.images.filterNot { it.pendingRemoval } != originalDraft.images,
    )

    fun setTitle(value: String) { updateDraft { it.copy(title = value) } }
    fun setBody(value: String) { updateDraft { it.copy(body = value) } }
    fun imagePickerFailed(e: Exception) {
        logEx(throwable = e, message = "Launch editor image picker failed")
        mutableStateFlow.update { it.copy(error = "无法打开系统相册，请重试") }
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
        if (uris.isEmpty() || route.documentId == null || draft.kind != "body" || !draft.loaded || draft.loading || draft.busy || draft.savedId != null) return
        mutableStateFlow.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val prepared = repository.prepareImages(draftSession, uris)
                val images = if (replacementId == null) draft.images + prepared.map { ImageDraft(prepared = it) }
                else draft.images.map { if (it.id == replacementId) ImageDraft(prepared = prepared.single()) else it }
                mutableStateFlow.value = changed(draft.copy(busy = false, images = images, error = null))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                logEx(throwable = e, message = "Prepare selected images failed")
                mutableStateFlow.update { it.copy(busy = false, error = e.message ?: "图片处理失败，请重试") }
            }
        }
    }

    fun save() {
        val draft = stateFlow.value
        if (draft.busy || draft.loading || !draft.loaded || draft.savedId != null || (route.nodeId != null && !draft.dirty)) return
        mutableStateFlow.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val result = if (route.documentId == null) repository.create(draft.title, draft.body)
                else repository.saveNode(route.documentId, route.nodeId, route.anchorId, draft.kind,
                    draft.title, draft.body, draft.images.filterNot { it.pendingRemoval })
                mutableStateFlow.update { it.copy(busy = false, dirty = false, savedId = result.value, mirrorFailed = result.mirrorFailed) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                logEx(throwable = e, message = "Save draft failed")
                mutableStateFlow.update { it.copy(busy = false, error = e.message ?: "保存失败，请重试") }
            }
        }
    }

    fun deleteNode() {
        val documentId = route.documentId ?: return
        val nodeId = route.nodeId ?: return
        val draft = stateFlow.value
        if (!draft.loaded || draft.fixed || draft.loading || draft.busy || draft.savedId != null) return
        mutableStateFlow.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val result = repository.deleteNode(documentId, nodeId)
                mutableStateFlow.update { it.copy(busy = false, dirty = false, savedId = nodeId, mirrorFailed = result.mirrorFailed) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                logEx(throwable = e, message = "Delete node failed")
                mutableStateFlow.update { it.copy(busy = false, error = e.message ?: "删除失败，请重试") }
            }
        }
    }

    class Factory(private val repository: DocumentRepository, private val route: EditRoute) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(EditorViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return EditorViewModel(repository, route) as T
        }
    }
}
