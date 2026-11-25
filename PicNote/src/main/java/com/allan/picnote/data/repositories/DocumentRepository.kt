package com.allan.picnote.data.repositories

import android.content.Context
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import com.allan.picnote.R
import com.allan.picnote.data.BluetoothArchiveDirectory
import com.allan.picnote.data.BluetoothArchiveException
import com.allan.picnote.data.BluetoothDocumentArchive
import com.allan.picnote.data.DocumentsDirectory
import com.allan.picnote.data.ExportDraftsDirectory
import com.allan.picnote.data.ImageDraftsDirectory
import com.allan.picnote.data.PicNoteDatabase
import com.allan.picnote.data.PicNoteCleaner
import com.allan.picnote.data.PicNoteStorageException
import com.allan.picnote.data.MarkdownBlockSeparator
import com.allan.picnote.data.SharedExportsDirectory
import com.allan.picnote.data.WelcomeDocumentId
import com.allan.picnote.data.WelcomeDocumentTemplate
import com.allan.picnote.data.WebDocumentHtml
import com.allan.picnote.data.bluetoothArchiveStorageFailure
import com.allan.picnote.data.deleteBluetoothArchiveFiles
import com.allan.picnote.data.entity.*
import com.allan.picnote.data.htmlEscape
import com.allan.picnote.data.markdown
import com.allan.picnote.data.markdownNodes
import com.allan.picnote.data.parseCodeBlock
import com.allan.picnote.data.unreferencedMedia
import com.allan.picnote.logEx
import com.allan.picnote.loge
import com.allan.picnote.media.ImagePreparation
import com.allan.picnote.media.MediaQueryBatchSize
import com.allan.picnote.media.MediaSourceStamp
import com.allan.picnote.media.PhotoAccess
import com.allan.picnote.media.PreparedImage
import com.allan.picnote.media.currentMediaVersions
import com.allan.picnote.media.mediaAddedTimes
import com.allan.picnote.media.mediaSource
import com.allan.picnote.media.photoAccess
import com.au.module_android.utils.withIOThread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.text.Normalizer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class Saved<T>(val value: T, val mirrorFailed: Boolean = false, val skippedImages: Int = 0)
data class ImageDraft(val existing: NodeImageEntity? = null, val prepared: PreparedImage? = null, val id: String = existing?.id ?: UUID.randomUUID().toString(), val pendingRemoval: Boolean = false)
data class HomeDocuments(val documents: List<DocumentSummary>, val content: DocumentContent?)
data class EditableNode(val node: NodeEntity, val images: List<NodeImageEntity>, val fixed: Boolean)

private const val NodeSortKeyStep = 1024L

private fun requireStorage(condition: Boolean, @StringRes messageRes: Int) {
    if (!condition) throw PicNoteStorageException(messageRes)
}

private fun checkedNodeTextBytes(text: String, available: Int = MaxNodeTextBytes): Int {
    requireStorage(text.length <= available, R.string.error_node_text_too_large)
    val bytes = text.encodeToByteArray().size
    requireStorage(bytes <= available, R.string.error_node_text_too_large)
    return bytes
}

/** 串行维护文档结构、WebP 副本、源 URI 映射与可重建的 Markdown 镜像。 */
class DocumentRepository(private val context: Context, private val database: PicNoteDatabase) {
    private val dao = database.dao()
    private val mutex = Mutex()
    private val preparationMutex = Mutex()
    private val root = File(context.filesDir, DocumentsDirectory)
    private val draftRoot = File(context.filesDir, ImageDraftsDirectory)
    private val exportDraftRoot = File(context.cacheDir, ExportDraftsDirectory)
    private val exportRoot = File(context.cacheDir, SharedExportsDirectory)
    private val activeDrafts = ConcurrentHashMap.newKeySet<String>()
    private val activeExports = ConcurrentHashMap.newKeySet<String>()
    // 只在 mutex 内维护文档素材的保留计数，清理同样在锁内读取。
    private val retainedExportMedia = mutableMapOf<String, MutableMap<String, Int>>()
    private val preparation = ImagePreparation(context)
    private val bluetoothArchive = BluetoothDocumentArchive(File(context.filesDir, BluetoothArchiveDirectory))
    private val preferences = context.getSharedPreferences("picnote", Context.MODE_PRIVATE)
    val cleaner = PicNoteCleaner(context, database, mutex, activeDrafts, activeExports, retainedExportMedia, ::writeMirror)

    fun imageFile(documentId: String, fileName: String): File = File(root, "$documentId/media/$fileName")

    fun documentDirectory(documentId: String): File = File(root, documentId)

    fun openDraftSession(): String = UUID.randomUUID().toString().also { activeDrafts.add(it) }

    suspend fun closeDraftSession(session: String) = withContext(Dispatchers.IO) {
        preparationMutex.withLock {
            mutex.withLock {
                activeDrafts.remove(session)
                try {
                    if (!File(draftRoot, session).deleteRecursively()) loge("Delete image draft files failed")
                } catch (e: Exception) { logEx(throwable = e, message = "Delete image draft files failed") }
            }
        }
    }

    suspend fun prepareImages(session: String, uris: List<String>): List<PreparedImage> = withContext(Dispatchers.IO) {
        preparationMutex.withLock { prepareBatch(session, uris) }
    }

    private suspend fun prepareBatch(session: String, uris: List<String>, candidates: List<PendingMediaEntity> = emptyList()): List<PreparedImage> {
        if (session !in activeDrafts) throw PicNoteStorageException(R.string.error_draft_closed)
        val directory = File(draftRoot, session)
        val sourceStamps = readSourceStamps(uris)
        validateCandidates(candidates, sourceStamps)
        val prepared = mutableListOf<PreparedImage>()
        try {
            uris.forEach { raw ->
                currentCoroutineContext().ensureActive()
                prepared += preparation.prepare(raw, directory).copy(sourceStamp = sourceStamps[raw])
            }
            return prepared
        } catch (e: Throwable) {
            prepared.forEach { it.file.delete() }
            throw e
        }
    }

    suspend fun documentSummaries(): List<DocumentSummary> = dao.documents()

    suspend fun home(requestedId: String? = null): HomeDocuments = withContext(Dispatchers.IO) {
        mutex.withLock {
            val documents = dao.documents()
            val previous = preferences.getString("last_document", null)
            val selected = requestedId ?: previous
            val id = documents.firstOrNull { it.id == selected }?.id ?: documents.firstOrNull()?.id
            if (id != null && id != previous) preferences.edit().putString("last_document", id).apply()
            HomeDocuments(documents, id?.let { withImportedSources(snapshot(it)) })
        }
    }

    suspend fun editableNode(documentId: String, nodeId: String): EditableNode = withIOThread {
        mutex.withLock {
            val node = dao.node(documentId, nodeId) ?: run {
                requireStorage(dao.document(documentId) != null, R.string.error_document_deleted_reselect)
                throw PicNoteStorageException(R.string.error_node_deleted)
            }
            EditableNode(node, dao.nodeImages(documentId, nodeId), dao.isFixedNode(documentId, nodeId))
        }
    }

    suspend fun contentIfExists(id: String): DocumentContent? = withContext(Dispatchers.IO) {
        mutex.withLock { dao.document(id)?.let { withImportedSources(snapshot(it)) } }
    }

    private suspend fun snapshot(id: String): DocumentContent {
        val document = dao.document(id) ?: throw PicNoteStorageException(R.string.error_document_deleted_reselect)
        return snapshot(document)
    }

    private suspend fun snapshot(document: DocumentEntity): DocumentContent {
        val id = document.id
        val images = dao.images(id).groupBy { it.nodeId }
        return DocumentContent(document, dao.nodes(id).map { NodeContent(it, images[it.id].orEmpty()) })
    }

    private suspend fun withImportedSources(content: DocumentContent): DocumentContent {
        if (photoAccess(context) != PhotoAccess.Full) return content
        val sources = dao.activeSourceImages(content.document.id)
        if (sources.isEmpty()) return content
        val versions = observedMediaVersions()
        val activeUris = sources.mapNotNullTo(mutableSetOf()) { image ->
            val volume = mediaSource(image.sourceUri)?.volume
            image.sourceUri.takeIf { volume != null && image.sourceVersion == versions[volume] }
        }
        return content.copy(importedSourceUris = activeUris)
    }

    suspend fun initializeWelcome(): Saved<String>? = withIOThread {
        mutex.withLock {
            if (preferences.getBoolean("welcome_initialized", false)) return@withLock null
            // 已有文档的安装不补插教程，用户删除教程后也不再重建。
            if (dao.documents().isNotEmpty()) {
                requireStorage(preferences.edit().putBoolean("welcome_initialized", true).commit(), R.string.error_save_welcome_state)
                return@withLock null
            }
            val language = if (Resources.getSystem().configuration.locales[0].language == "zh") "zh-CN" else "en"
            val assetDirectory = "welcome/$language"
            val template = context.assets.open("$assetDirectory/document.json").bufferedReader(Charsets.UTF_8).use {
                Json.decodeFromString<WelcomeDocumentTemplate>(it.readText())
            }
            val id = WelcomeDocumentId
            val imageNodeId = "$id-image"
            val directory = File(root, "$id/media")
            val imageName = if (template.imageFileName in retainedExportMedia[id].orEmpty()) {
                "${UUID.randomUUID()}.webp"
            } else template.imageFileName
            val image = File(directory, imageName)
            val temporaryImage = File(directory, ".welcome.tmp")
            var committed = false
            try {
                requireStorage(directory.isDirectory || directory.mkdirs(), R.string.error_create_welcome_media_dir)
                context.assets.open("$assetDirectory/${template.imageFileName}").use { input ->
                    FileOutputStream(temporaryImage).use { output -> input.copyTo(output); output.fd.sync() }
                }
                currentCoroutineContext().ensureActive()
                Files.move(temporaryImage.toPath(), image.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                withContext(NonCancellable) {
                    database.withTransaction {
                        dao.putDocument(DocumentEntity(id, template.title, System.currentTimeMillis()))
                        dao.putNodes(listOf(
                            NodeEntity("$id-title", id, 1024, "title", title = template.title),
                            NodeEntity("$id-introduction", id, 2048, "introduction", template.introduction),
                            NodeEntity(imageNodeId, id, 3072, "body"),
                            NodeEntity("$id-instruction", id, 4096, "body", template.instruction),
                            NodeEntity("$id-todo", id, 5120, "chapter", title = template.todoTitle),
                        ))
                        dao.putImages(listOf(NodeImageEntity("$id-launcher", imageNodeId, id, 0, imageName,
                            template.imageSha256, template.imageWidth, template.imageHeight)))
                    }
                    committed = true
                    requireStorage(preferences.edit().putBoolean("welcome_initialized", true).putString("last_document", id).commit(),
                        R.string.error_save_welcome_state)
                    Saved(id, !writeMirror(id))
                }
            } finally {
                if (!committed) {
                    if (retainedExportMedia[id].isNullOrEmpty()) File(root, id).deleteRecursively()
                    else {
                        temporaryImage.delete()
                        image.delete()
                    }
                }
            }
        }
    }

    suspend fun create(title: String, firstBody: String): Saved<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val savedTitle = title.trim()
            requireStorage(savedTitle.isNotEmpty(), R.string.error_title_required)
            checkedNodeTextBytes(savedTitle)
            checkedNodeTextBytes(firstBody)
            val id = UUID.randomUUID().toString()
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) {
                database.withTransaction {
                    dao.putDocument(DocumentEntity(id, savedTitle, System.currentTimeMillis()))
                    dao.putNodes(listOf(
                        NodeEntity(UUID.randomUUID().toString(), id, 1024, "title", title = savedTitle),
                        NodeEntity(UUID.randomUUID().toString(), id, 2048, "introduction", firstBody),
                    ))
                }
                preferences.edit().putString("last_document", id).apply()
                Saved(id, !writeMirror(id))
            }
        }
    }

    suspend fun deleteDocument(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            database.withTransaction { dao.deleteDocument(id) }
            removeDeletedDocumentFiles(id)
        }
    }

    /** 复制整份文档：标题追加 (V2)，节点、图片记录与媒体文件各自独立，镜像按新标题重建。 */
    suspend fun duplicate(id: String): Saved<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val source = snapshot(id)
            fun unreadable(number: Int, cause: IOException?): PicNoteStorageException = PicNoteStorageException(
                R.string.error_duplicate_unreadable_image, cause, listOf(source.document.title, number),
            )
            val title = "${source.document.title}(V2)"
            for (entry in source.nodes) {
                val node = entry.node
                val nodeTitle = if (node.kind == "title") title else node.title
                val titleBytes = checkedNodeTextBytes(nodeTitle)
                checkedNodeTextBytes(node.bodyMarkdown, MaxNodeTextBytes - titleBytes)
            }
            val files = trustedMedia(source, ::unreadable)
            val newId = UUID.randomUUID().toString()
            val directory = File(root, newId)
            var committed = false
            try {
                if (files.isNotEmpty()) {
                    val target = File(directory, "media")
                    requireStorage(target.isDirectory || target.mkdirs(), R.string.error_create_image_dir)
                    files.forEach { (file, number) ->
                        File(target, file.name).outputStream().use { output ->
                            file.copyBytesTo(output) { e -> unreadable(number, e) }
                        }
                    }
                }
                currentCoroutineContext().ensureActive()
                withContext(NonCancellable) {
                    database.withTransaction {
                        dao.putDocument(DocumentEntity(newId, title, System.currentTimeMillis()))
                        val nodeIds = source.nodes.associate { it.node.id to UUID.randomUUID().toString() }
                        dao.putNodes(source.nodes.map { node ->
                            node.node.copy(id = nodeIds.getValue(node.node.id), documentId = newId,
                                title = if (node.node.kind == "title") title else node.node.title)
                        })
                        val images = source.nodes.flatMap { node ->
                            node.images.map { image ->
                                image.copy(id = UUID.randomUUID().toString(), nodeId = nodeIds.getValue(node.node.id), documentId = newId)
                            }
                        }
                        dao.putImages(images)
                        dao.putSourceImages(dao.sourceImages(id).map { it.copy(documentId = newId) })
                    }
                    committed = true
                    preferences.edit().putString("last_document", newId).apply()
                    Saved(newId, !writeMirror(newId))
                }
            } finally {
                if (!committed) directory.deleteRecursively()
            }
        }
    }

    suspend fun deleteNode(documentId: String, nodeId: String): Saved<Unit> = mutate(documentId) {
        val index = dao.nodes(documentId).indexOfFirst { it.id == nodeId }
        requireStorage(index >= 0, R.string.error_node_deleted)
        requireStorage(index >= 2, R.string.error_fixed_node_delete)
        dao.deleteNode(nodeId)
    }

    suspend fun move(documentId: String, nodeId: String, beforeId: String?): Saved<Unit>? {
        if (beforeId == nodeId) return null
        return mutate(documentId, cleanupMedia = false) {
            val nodes = dao.nodes(documentId).toMutableList()
            val sourceIndex = nodes.indexOfFirst { it.id == nodeId }
            requireStorage(sourceIndex >= 0, R.string.error_node_deleted)
            requireStorage(sourceIndex >= 2, R.string.error_fixed_node_move)
            val node = nodes.removeAt(sourceIndex)
            val index = if (beforeId == null) nodes.size else nodes.indexOfFirst { it.id == beforeId }
            requireStorage(index >= 0, R.string.error_node_deleted)
            requireStorage(index >= 2, R.string.error_move_before_fixed)
            nodes.add(index, node)
            placeKey(nodes, index)
        }
    }

    private suspend fun placeKey(order: List<NodeEntity>, index: Int) {
        val previous = order[index - 1].sortKey
        val next = order.getOrNull(index + 1)?.sortKey
        val key = when {
            next == null && previous <= Long.MAX_VALUE - NodeSortKeyStep -> previous + NodeSortKeyStep
            // 计算有符号中点时避免相减溢出。
            next != null && next > Long.MIN_VALUE && previous < next - 1 -> (previous and next) + ((previous xor next) shr 1)
            else -> null
        }
        if (key == null) renumber(order)
        else dao.setSortKey(order[index].id, key)
    }

    private suspend fun renumber(order: List<NodeEntity>): Long {
        order.forEachIndexed { index, node -> dao.setSortKey(node.id, (index + 1L) * NodeSortKeyStep) }
        return order.size * NodeSortKeyStep
    }

    suspend fun mergeNodes(documentId: String, nodeIds: List<String>): Saved<String> = mutate(documentId) {
        val ids = nodeIds.toSet()
        requireStorage(ids.size >= 2 && ids.size == nodeIds.size, R.string.error_select_body_nodes)
        val content = snapshot(documentId)
        val selected = content.nodes.filter { it.node.id in ids }
        requireStorage(selected.size == ids.size && selected.all { it.node.kind == "body" }, R.string.error_merge_nodes_changed)
        // 以当前文档顺序为准，先转移引用，再删除较后节点，避免级联删除图片。
        val earlier = selected.first()
        val body = buildString {
            var remaining = MaxNodeTextBytes
            for (entry in selected) {
                val text = entry.node.bodyMarkdown
                if (text.isBlank()) continue
                if (isNotEmpty()) {
                    remaining -= checkedNodeTextBytes(MarkdownBlockSeparator, remaining)
                    append(MarkdownBlockSeparator)
                }
                remaining -= checkedNodeTextBytes(text, remaining)
                append(text)
            }
        }
        dao.updateNode(earlier.node.id, "body", body, "")
        dao.putImages(selected.flatMap { it.images }.mapIndexed { index, image ->
            image.copy(nodeId = earlier.node.id, sortIndex = index)
        })
        selected.drop(1).forEach { dao.deleteNode(it.node.id) }
        earlier.node.id
    }

    suspend fun moveImage(documentId: String, nodeId: String, imageId: String, direction: Int): Saved<Unit> = mutate(documentId, cleanupMedia = false) {
        requireStorage(direction == -1 || direction == 1, R.string.error_invalid_image_direction)
        val images = dao.nodeImages(documentId, nodeId)
        if (images.isEmpty()) requireStorage(dao.node(documentId, nodeId) != null, R.string.error_node_deleted)
        val index = images.indexOfFirst { it.id == imageId }
        requireStorage(index >= 0, R.string.error_image_deleted)
        val adjacent = images.getOrNull(index + direction) ?: throw PicNoteStorageException(R.string.error_image_at_edge)
        val image = images[index]
        dao.putImages(listOf(image.copy(sortIndex = adjacent.sortIndex), adjacent.copy(sortIndex = image.sortIndex)))
    }

    suspend fun saveNode(
        documentId: String,
        nodeId: String?,
        anchorId: String?,
        kind: String,
        title: String,
        body: String,
        images: List<ImageDraft>,
    ): Saved<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val original = snapshot(documentId)
            val currentIndex = original.nodes.indexOfFirst { it.node.id == nodeId }
            val current = original.nodes.getOrNull(currentIndex)
            if (nodeId != null) requireStorage(current != null, R.string.error_node_deleted_retry)
            val savedKind = current?.node?.kind ?: kind
            requireStorage(savedKind in listOf("title", "introduction", "chapter", "body", "code"), R.string.error_invalid_node_type)
            requireStorage(nodeId != null || savedKind != "title", R.string.error_only_one_title)
            requireStorage(nodeId != null || savedKind != "introduction", R.string.error_create_introduction)
            requireStorage(current == null || savedKind == kind, R.string.error_change_node_type)
            requireStorage(savedKind == "body" || images.isEmpty(), R.string.error_node_images_not_allowed)
            val anchorIndex = if (anchorId == null) original.nodes.size else original.nodes.indexOfFirst { it.node.id == anchorId }
            if (nodeId == null) requireStorage(anchorIndex >= 2, R.string.error_insert_before_fixed)
            requireStorage(savedKind !in listOf("title", "chapter") || title.trim().isNotEmpty(), R.string.error_title_required)
            requireStorage(savedKind != "introduction" || currentIndex == 1 || body.isNotBlank(), R.string.error_introduction_required)
            val savedTitle = if (savedKind == "title" || savedKind == "chapter") title.trim() else ""
            val savedBody = if (savedKind in listOf("body", "introduction", "code")) body else ""
            val titleBytes = checkedNodeTextBytes(savedTitle)
            checkedNodeTextBytes(savedBody, MaxNodeTextBytes - titleBytes)
            val code = if (savedKind == "code") parseCodeBlock(body) else null
            val oldBody = current?.node?.bodyMarkdown
            requireStorage(savedKind != "code" || code?.code?.isNotBlank() == true ||
                (code == null && oldBody != null && parseCodeBlock(oldBody) == null && body.isNotBlank()), R.string.error_code_required)
            val createdFiles = mutableListOf<File>()
            var committed = false
            try {
                val id = nodeId ?: UUID.randomUUID().toString()
                val byHash = original.nodes.filter { it.node.id != nodeId }.flatMap { it.images }.associateBy { it.hash }.toMutableMap()
                val currentImages = current?.images.orEmpty().associateBy { it.id }
                val retained = images.mapNotNull { it.existing }.map { existing ->
                    currentImages[existing.id] ?: throw PicNoteStorageException(R.string.error_image_expired)
                }.associateBy { it.hash }
                val previousByHash = currentImages.values.associateBy { it.hash }
                val savedImages = mutableListOf<NodeImageEntity>()
                val mappings = mutableListOf<Pair<PreparedImage, String>>()
                var skippedImages = 0
                images.forEach { draft ->
                    currentCoroutineContext().ensureActive()
                    val existing = draft.existing
                    if (existing != null) {
                        val image = currentImages.getValue(existing.id)
                        savedImages += image.copy(sortIndex = savedImages.size)
                    } else {
                        val prepared = draft.prepared ?: throw PicNoteStorageException(R.string.error_image_not_ready)
                        val duplicate = retained[prepared.hash] ?: byHash[prepared.hash]
                        val reusable = previousByHash[prepared.hash]?.takeIf { imageFile(documentId, it.fileName).isReadableMedia() }
                        val image = duplicate ?: reusable?.copy(sourceUri = prepared.sourceUri, sortIndex = savedImages.size)
                            ?: copyPreparedImage(documentId, id, prepared, savedImages.size, createdFiles)
                        if (duplicate == null) {
                            savedImages += image
                            byHash[image.hash] = image
                        } else {
                            requireStorage(imageFile(documentId, duplicate.fileName).isReadableMedia(), R.string.image_unreadable_replace)
                            skippedImages++
                        }
                        mappings += prepared to image.fileName
                    }
                }
                requireStorage(
                    savedKind != "body" || body.isNotBlank() || savedImages.isNotEmpty(),
                    if (skippedImages > 0) R.string.error_duplicate_image else R.string.error_body_required,
                )
                val sources = checkSourceMappings(documentId, mappings)
                val keptImages = savedImages.mapTo(hashSetOf()) { it.id }
                val removedImages = currentImages.keys.any { it !in keptImages }
                currentCoroutineContext().ensureActive()
                withContext(NonCancellable) {
                    database.withTransaction {
                        requireStorage(dao.document(documentId) != null, R.string.error_document_deleted)
                        val currentNodes = dao.nodes(documentId)
                        if (nodeId == null) {
                            val index = if (anchorId == null) currentNodes.size else currentNodes.indexOfFirst { it.id == anchorId }
                            requireStorage(index >= 2, R.string.error_insert_anchor_changed)
                            val node = NodeEntity(id, documentId, 0, savedKind, savedBody, savedTitle)
                            dao.putNodes(listOf(node))
                            val order = currentNodes.toMutableList()
                            order.add(index, node)
                            placeKey(order, index)
                        } else {
                            requireStorage(currentNodes.any { it.id == nodeId && it.kind == savedKind }, R.string.error_node_changed_retry)
                            dao.updateNode(id, savedKind, savedBody, savedTitle)
                            dao.deleteImages(id)
                            if (savedKind == "title") dao.updateDocumentTitle(documentId, savedTitle, System.currentTimeMillis())
                        }
                        dao.putImages(savedImages)
                        saveSourceMappings(sources)
                        if (removedImages) dao.cleanSourceImages(documentId)
                        dao.touchDocument(documentId, System.currentTimeMillis())
                    }
                    committed = true
                    afterCommit(documentId, id, cleanupMedia = removedImages, skippedImages = skippedImages)
                }
            } finally {
                if (!committed) createdFiles.forEach { it.delete() }
            }
        }
    }

    suspend fun importImages(
        documentId: String,
        uris: List<String>,
        candidateImages: List<PendingMediaEntity> = emptyList(),
        batchToConsume: List<PendingMediaEntity> = candidateImages,
    ): Saved<Int> = withContext(Dispatchers.IO) {
        requireStorage(dao.document(documentId) != null, R.string.error_document_deleted_reselect)
        val selected = uris.distinct()
        if (candidateImages.isNotEmpty() && candidateImages.map { it.uri }.toSet() != selected.toSet()) {
            throw PicNoteStorageException(R.string.pending_images_changed)
        }
        val session = openDraftSession()
        try {
            // 这次导入独占草稿会话，正式文档在转码后重新读取。
            val prepared = preparationMutex.withLock { prepareBatch(session, selected, candidateImages) }
            mutex.withLock {
                val original = snapshot(documentId)
                val byHash = original.nodes.flatMap { it.images }.associateBy { it.hash }.toMutableMap()
                val createdFiles = mutableListOf<File>()
                var committed = false
                try {
                    val nodes = mutableListOf<NodeEntity>()
                    val images = mutableListOf<NodeImageEntity>()
                    val mappings = mutableListOf<Pair<PreparedImage, String>>()
                    prepared.forEach { source ->
                        currentCoroutineContext().ensureActive()
                        val nodeId = UUID.randomUUID().toString()
                        val duplicate = byHash[source.hash]
                        if (duplicate != null) {
                            requireStorage(imageFile(documentId, duplicate.fileName).isReadableMedia(), R.string.image_unreadable_replace)
                        }
                        val image = duplicate ?: copyPreparedImage(documentId, nodeId, source, 0, createdFiles)
                        mappings += source to image.fileName
                        if (duplicate == null) {
                            nodes += NodeEntity(nodeId, documentId, 0, "body")
                            images += image
                            byHash[image.hash] = image
                        }
                    }
                    val sources = checkSourceMappings(documentId, mappings, candidateImages)
                    currentCoroutineContext().ensureActive()
                    withContext(NonCancellable) {
                        database.withTransaction {
                            if (dao.document(documentId) == null) throw PicNoteStorageException(R.string.error_document_deleted_reselect)
                            val current = original.nodes.map { it.node }
                            if (nodes.isNotEmpty()) {
                                val previous = current.last().sortKey
                                val end = if (previous > Long.MAX_VALUE - nodes.size * NodeSortKeyStep) renumber(current) else previous
                                dao.putNodes(nodes.mapIndexed { index, node -> node.copy(sortKey = end + (index + 1L) * NodeSortKeyStep) })
                            }
                            dao.putImages(images)
                            saveSourceMappings(sources, candidateImages, batchToConsume)
                            dao.cleanSourceImages(documentId)
                            if (nodes.isNotEmpty()) dao.touchDocument(documentId, System.currentTimeMillis())
                        }
                        committed = true
                        afterCommit(documentId, nodes.size, cleanupMedia = nodes.isNotEmpty(), skippedImages = prepared.size - nodes.size)
                    }
                } finally {
                    if (!committed) createdFiles.forEach { it.delete() }
                }
            }
        } finally { withContext(NonCancellable) { closeDraftSession(session) } }
    }

    private suspend fun readSourceStamps(uris: Collection<String>): Map<String, MediaSourceStamp> {
        if (photoAccess(context) != PhotoAccess.Full) return emptyMap()
        val sources = uris.distinct().mapNotNull { uri -> mediaSource(uri)?.let { uri to it } }.groupBy { it.second.volume }
        if (sources.isEmpty()) return emptyMap()
        val versions = observedMediaVersions(sources.keys)
        val stamps = buildMap {
            for ((volume, references) in sources) {
                val version = versions[volume] ?: continue
                val times = try {
                    mediaAddedTimes(context, volume, references.map { it.second.id })
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logEx(throwable = e, message = "Read MediaStore image identities failed")
                    continue
                }
                for ((uri, source) in references) {
                    times[source.id]?.let { put(uri, MediaSourceStamp(volume, version, it)) }
                }
            }
        }
        if (photoAccess(context) != PhotoAccess.Full || stamps.isEmpty()) return emptyMap()
        val currentVersions = observedMediaVersions(stamps.values.mapTo(mutableSetOf()) { it.volume })
        return stamps.filterValues { it.version == currentVersions[it.volume] }
    }

    private fun observedMediaVersions(volumes: Set<String>? = null): Map<String, String> = try {
        currentMediaVersions(context, volumes)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logEx(throwable = e, message = "Read MediaStore volume versions failed")
        emptyMap()
    }

    private fun validateCandidates(candidates: List<PendingMediaEntity>, stamps: Map<String, MediaSourceStamp>) {
        if (candidates.isEmpty()) return
        if (photoAccess(context) != PhotoAccess.Full) throw PicNoteStorageException(R.string.error_photo_access_changed)
        if (candidates.any { stamps[it.uri] != MediaSourceStamp(it.volume, it.version, it.addedAt) }) {
            throw PicNoteStorageException(R.string.error_media_library_changed)
        }
    }

    private data class SourceMappings(val history: List<SourceImageEntity>, val verified: List<SourceImageEntity>)

    private suspend fun checkSourceMappings(
        documentId: String,
        mappings: List<Pair<PreparedImage, String>>,
        candidates: List<PendingMediaEntity> = emptyList(),
    ): SourceMappings {
        val withStamps = mappings.filter { it.first.sourceStamp != null }
        val stamps = readSourceStamps(withStamps.map { it.first.sourceUri })
        validateCandidates(candidates, stamps)
        val verified = withStamps.mapNotNull { (image, fileName) ->
            val stamp = stamps[image.sourceUri]
            if (stamp == null || stamp != image.sourceStamp) null
            else SourceImageEntity(image.sourceUri, documentId, fileName, stamp.version, stamp.addedAt)
        }
        val history = mappings.map { (image, fileName) -> SourceImageEntity(image.sourceUri, documentId, fileName) }
        return SourceMappings(history, verified)
    }

    private suspend fun saveSourceMappings(
        sources: SourceMappings,
        candidates: List<PendingMediaEntity> = emptyList(),
        batchToConsume: List<PendingMediaEntity> = candidates,
    ) {
        if (sources.history.isNotEmpty()) dao.keepSourceImages(sources.history)
        if (sources.verified.isNotEmpty()) dao.putSourceImages(sources.verified)
        if (sources.verified.isEmpty() && batchToConsume.isEmpty() && candidates.isEmpty()) return
        val pending = dao.pending().associateBy { it.uri }
        if (candidates.any { pending[it.uri] != it }) throw PicNoteStorageException(R.string.pending_images_changed)
        val kept = sources.verified.mapNotNull { source ->
            val candidate = pending[source.sourceUri]
            source.sourceUri.takeIf { candidate != null && source.sourceVersion == candidate.version && source.sourceAddedAt == candidate.addedAt }
        }
        val consumed = kept + batchToConsume.filter { pending[it.uri] == it }.map { it.uri }
        consumed.distinct().chunked(MediaQueryBatchSize).forEach { dao.consumePending(it) }
    }

    private suspend fun copyPreparedImage(documentId: String, nodeId: String, image: PreparedImage, index: Int,
        createdFiles: MutableList<File>): NodeImageEntity {
        if (!image.file.isFile || !image.file.canRead()) throw PicNoteStorageException(R.string.error_image_draft_unreadable)
        val directory = File(root, "$documentId/media")
        if (!directory.isDirectory && !directory.mkdirs()) throw PicNoteStorageException(R.string.error_create_image_dir)
        val canonicalName = "${image.hash}.webp"
        val fileName = if (File(directory, canonicalName).exists()) "${UUID.randomUUID()}.webp" else canonicalName
        val target = File(directory, fileName)
        val temporary = File(directory, ".${UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                image.file.copyBytesTo(output)
                output.fd.sync()
            }
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            createdFiles += target
        } finally { temporary.delete() }
        return NodeImageEntity(UUID.randomUUID().toString(), nodeId, documentId, index, fileName, image.hash,
            image.width, image.height, image.sourceUri)
    }

    private suspend fun <T> mutate(id: String, cleanupMedia: Boolean = true, block: suspend () -> T): Saved<T> = withContext(Dispatchers.IO) {
        mutex.withLock {
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) {
                val value = database.withTransaction {
                    requireStorage(dao.document(id) != null, R.string.error_document_deleted)
                    val result = block()
                    dao.touchDocument(id, System.currentTimeMillis())
                    if (cleanupMedia) dao.cleanSourceImages(id)
                    result
                }
                afterCommit(id, value, cleanupMedia)
            }
        }
    }

    private suspend fun <T> afterCommit(id: String, value: T, cleanupMedia: Boolean = true, skippedImages: Int = 0): Saved<T> {
        if (cleanupMedia) {
            try { cleanMedia(id) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { logEx(throwable = e, message = "Clean media after commit failed") }
        }
        return Saved(value, !writeMirror(id), skippedImages)
    }

    suspend fun retryMirror(id: String): Boolean = withContext(Dispatchers.IO) { mutex.withLock { writeMirror(id) } }

    private suspend fun writeMirror(id: String): Boolean {
        val temporary = File(root, "$id/.doc.md.tmp")
        return try {
            temporary.parentFile!!.mkdirs()
            FileOutputStream(temporary).use { stream ->
                stream.write(snapshot(id).markdown().toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            Files.move(temporary.toPath(), File(root, "$id/doc.md").toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logEx(throwable = e, message = "Write document mirror failed")
            false
        } finally {
            temporary.delete()
        }
    }

    /** 返回私有会话目录中的 ZIP，调用方在会话结束后清理文件和父目录。 */
    suspend fun prepareBluetoothArchive(documentId: String, digest: MessageDigest? = null): File {
        var sessionDirectory: File? = null
        try {
            return withIOThread {
                val session = bluetoothArchive.openSession("send-")
                sessionDirectory = session
                val media = Files.createDirectory(File(session, "snapshot").toPath()).toFile()
                val snapshot = mutex.withLock {
                    val document = dao.document(documentId) ?: throw BluetoothArchiveException(R.string.error_document_deleted_reselect)
                    bluetoothArchive.copySnapshot(media, snapshot(document)) { imageFile(documentId, it) }
                }
                val file = File(session, exportFileName(snapshot.manifest.title, "zip"))
                bluetoothArchive.write(file, snapshot, digest)
                deleteBluetoothArchiveFiles(media)
                file
            }
        } catch (e: Exception) {
            // 切回调用线程时被取消也需要释放尚未交给调用方的文件。
            withContext(NonCancellable + Dispatchers.IO) {
                sessionDirectory?.let(::deleteBluetoothArchiveFiles)
            }
            if (e is CancellationException || e is BluetoothArchiveException) throw e
            logEx(throwable = e, message = "Prepare Bluetooth archive failed")
            throw bluetoothArchiveStorageFailure(e)
        }
    }

    /** 验证独立暂存目录后，按清单顺序提交文档和素材。 */
    suspend fun importBluetoothArchive(archive: File, onCommitting: suspend () -> Unit = {}): Saved<String> = withIOThread {
        try {
            val session = bluetoothArchive.openSession("import-")
            try {
                val media = File(session, "media")
                val imported = bluetoothArchive.read(archive, media)
                val documentId = UUID.randomUUID().toString()
                val images = mutableListOf<NodeImageEntity>()
                val nodes = imported.nodes.mapIndexed { index, node ->
                    currentCoroutineContext().ensureActive()
                    val nodeId = UUID.randomUUID().toString()
                    node.images.forEachIndexed { sortIndex, image ->
                        images += NodeImageEntity(
                            UUID.randomUUID().toString(), nodeId, documentId, sortIndex,
                            image.fileName, image.hash, image.width, image.height,
                        )
                    }
                    NodeEntity(nodeId, documentId, (index + 1L) * 1024, node.kind, node.bodyMarkdown, node.title)
                }
                mutex.withLock {
                    onCommitting()
                    currentCoroutineContext().ensureActive()
                    withContext(NonCancellable) {
                        val directory = documentDirectory(documentId)
                        var committed = false
                        var createdDirectory = false
                        try {
                            if (images.isNotEmpty()) {
                                Files.createDirectories(root.toPath())
                                Files.createDirectory(directory.toPath())
                                createdDirectory = true
                                Files.move(media.toPath(), File(directory, "media").toPath(), StandardCopyOption.ATOMIC_MOVE)
                            }
                            database.withTransaction {
                                dao.putDocument(DocumentEntity(documentId, imported.title, System.currentTimeMillis()))
                                dao.putNodes(nodes)
                                dao.putImages(images)
                            }
                            committed = true
                            Saved(documentId, !writeMirror(documentId))
                        } finally {
                            if (!committed && createdDirectory) deleteBluetoothArchiveFiles(directory)
                        }
                    }
                }
            } finally {
                deleteBluetoothArchiveFiles(session)
            }
        } catch (e: Exception) {
            if (e is CancellationException || e is BluetoothArchiveException) throw e
            logEx(throwable = e, message = "Import Bluetooth archive failed")
            throw bluetoothArchiveStorageFailure(e)
        }
    }

    /** 完整导出：Markdown 与图片素材分别成文件的 ZIP，便于继续编辑。 */
    suspend fun exportFull(id: String): File = exportArtifact(id, "zip") { snapshot, temporary ->
        ZipOutputStream(FileOutputStream(temporary)).use { zip ->
            zip.putNextEntry(ZipEntry("${snapshot.fileName.removeSuffix(".zip")}.md"))
            zip.write(snapshot.content.markdown().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            snapshot.media.forEach { (path, file) ->
                currentCoroutineContext().ensureActive()
                zip.putNextEntry(ZipEntry(path))
                file.copyBytesTo(zip)
                zip.closeEntry()
            }
        }
    }

    /** 网页导出：单文件 HTML，图片以 base64 内嵌，直接用浏览器打开即可阅读。 */
    suspend fun exportWeb(id: String): File = exportArtifact(id, "html") { snapshot, temporary ->
        val html = WebDocumentHtml(snapshot.media, currentCoroutineContext())
        FileOutputStream(temporary).use { stream ->
            val writer = OutputStreamWriter(stream, Charsets.UTF_8)
            writer.write(htmlHeader(snapshot.content.document.title))
            html.write(snapshot.content.markdownNodes(), writer)
            writer.write("</main></body></html>\n")
            writer.flush()
            stream.fd.sync()
        }
    }

    private data class ExportSnapshot(val content: DocumentContent, val media: Map<String, File>, val fileName: String)

    private suspend fun exportArtifact(
        id: String,
        extension: String,
        write: suspend (ExportSnapshot, File) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        // 暂存与最终文件同在 cacheDir，只把完成的文件移入 FileProvider 共享目录。
        val draft = File(exportDraftRoot, UUID.randomUUID().toString())
        val temporary = File(draft, "export.tmp")
        val stagedMedia = File(draft, "media")
        val retainedNames = mutableListOf<String>()
        var registered = false
        try {
            val frozen = mutex.withLock {
                val content = snapshot(id)
                val media = trustedMedia(content)
                requireStorage(draft.mkdirs(), R.string.error_create_export_dir)
                activeExports.add(draft.name)
                registered = true
                val staged = if (media.isEmpty()) emptyMap() else {
                    requireStorage(stagedMedia.mkdir(), R.string.error_create_export_dir)
                    media.keys.associate { source ->
                        currentCoroutineContext().ensureActive()
                        val target = File(stagedMedia, source.name)
                        val file = if (linkExportMedia(source, target)) target
                        else {
                            // 保留原路径到导出完成，写入阶段逐张读，不在锁内复制或同时打开所有文件。
                            val retained = retainedExportMedia.getOrPut(id) { mutableMapOf() }
                            retained[source.name] = (retained[source.name] ?: 0) + 1
                            retainedNames += source.name
                            source
                        }
                        "media/${source.name}" to file
                    }
                }
                ExportSnapshot(content, staged, exportFileName(content.document.title, extension))
            }
            write(frozen, temporary)
            currentCoroutineContext().ensureActive()
            mutex.withLock {
                val directory = File(exportRoot, draft.name)
                requireStorage(directory.mkdirs(), R.string.error_create_export_dir)
                val output = File(directory, frozen.fileName)
                try {
                    Files.move(temporary.toPath(), output.toPath(), StandardCopyOption.ATOMIC_MOVE)
                    output
                } finally {
                    if (!output.isFile) directory.deleteRecursively()
                }
            }
        } finally {
            if (registered) {
                try {
                    if (!draft.deleteRecursively()) loge("Delete temporary export files failed")
                } finally {
                    activeExports.remove(draft.name)
                    if (retainedNames.isNotEmpty()) releaseExportMedia(id, retainedNames)
                }
            }
        }
    }

    private fun linkExportMedia(source: File, target: File): Boolean {
        // 素材以新文件替换，硬链接可在正式目录删除后继续读出原字节。
        return try {
            Files.createLink(target.toPath(), source.toPath())
            true
        } catch (_: IOException) {
            false
        } catch (_: UnsupportedOperationException) {
            false
        }
    }

    private suspend fun releaseExportMedia(id: String, names: List<String>) = withContext(NonCancellable) {
        mutex.withLock {
            val retained = retainedExportMedia.getValue(id)
            var canClean = false
            for (name in names) {
                val count = retained.getValue(name)
                if (count == 1) {
                    retained.remove(name)
                    canClean = true
                } else retained[name] = count - 1
            }
            if (retained.isEmpty()) retainedExportMedia.remove(id)
            if (!canClean) return@withLock
            try {
                if (dao.document(id) == null) removeDeletedDocumentFiles(id) else cleanMedia(id)
            } catch (e: Exception) {
                logEx(throwable = e, message = "Clean released export media failed")
            }
        }
    }

    private suspend fun File.copyBytesTo(output: OutputStream, onReadError: (IOException) -> Exception = { it }) {
        val input = try { inputStream() } catch (e: IOException) { throw onReadError(e) }
        input.use {
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = try { input.read(buffer) } catch (e: IOException) { throw onReadError(e) }
                if (count < 0) break
                output.write(buffer, 0, count)
            }
        }
    }

    private fun File.isReadableMedia(): Boolean = isFile && canRead() && length() > 0

    private fun trustedMedia(
        content: DocumentContent,
        unreadable: (Int, IOException?) -> PicNoteStorageException = { _, cause ->
            PicNoteStorageException(R.string.error_export_image_unreadable, cause)
        },
    ): Map<File, Int> {
        val files = content.nodes.flatMap { it.images }
        if (files.isEmpty()) return emptyMap()
        val media = try {
            File(root.canonicalFile, "${content.document.id}/media")
        } catch (e: IOException) {
            throw unreadable(1, e)
        }
        val validName = Regex("[A-Za-z0-9][A-Za-z0-9._-]*\\.webp")
        return buildMap {
            files.forEachIndexed { index, image ->
                if (!validName.matches(image.fileName)) throw unreadable(index + 1, null)
                val file = File(media, image.fileName)
                if (file in this) return@forEachIndexed
                val readable = try {
                    file.canonicalFile == file && file.isReadableMedia()
                } catch (e: IOException) {
                    throw unreadable(index + 1, e)
                }
                if (!readable) throw unreadable(index + 1, null)
                put(file, index + 1)
            }
        }
    }

    private fun htmlHeader(title: String): String {
        val lang = ContextCompat.getContextForLanguage(context).resources.configuration.locales[0].toLanguageTag()
        return """<!DOCTYPE html>
<html lang="$lang">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${htmlEscape(title)}</title>
<style>
:root { color-scheme: light dark; }
body { margin: 0; padding: 24px 16px 48px; background: #fafafa; color: #1b1b1b;
  font: 16px/1.75 -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif; }
main { max-width: 720px; margin: 0 auto; padding: 28px 24px 32px; background: #fff; border-radius: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, .08); }
h1 { margin: 8px 0 24px; font-size: 26px; text-align: center; }
h2 { margin: 28px 0 12px; padding-bottom: 6px; font-size: 20px; border-bottom: 1px solid #e5e5e5; }
p { margin: 12px 0; }
img { display: block; max-width: 100%; height: auto; margin: 12px auto; border-radius: 8px; }
blockquote { margin: 16px 0; padding: 8px 16px; background: #f2f2f2; border-radius: 8px; color: #555; }
blockquote p { margin: 6px 0; }
a { color: #1a73e8; }
pre { overflow-x: auto; padding: 12px 16px; background: #f2f2f2; border-radius: 8px; }
pre code { font: 14px/1.6 ui-monospace, SFMono-Regular, Consolas, monospace; }
@media (prefers-color-scheme: dark) {
  body { background: #121212; color: #e6e6e6; }
  main { background: #1e1e1e; box-shadow: none; }
  h2 { border-bottom-color: #333; }
  blockquote { background: #262626; color: #b8b8b8; }
  pre { background: #262626; }
}
</style>
</head>
<body><main>
"""
    }

    private fun exportFileName(title: String, extension: String): String {
        val cleaned = Normalizer.normalize(title, Normalizer.Form.NFC)
            .replace(Regex("""[<>:"/\\|?*\p{Cc}\p{Cs}()\[\]{},，（）【】]+"""), "-")
            .replace(Regex("""[\s\p{Z}-]+"""), "-")
            .trim('.', '-')
            .ifBlank { "document" }
        val reserved = Regex("""CON|PRN|AUX|NUL|COM[1-9¹²³]|LPT[1-9¹²³]""", RegexOption.IGNORE_CASE)
        val name = if (reserved.matches(cleaned.substringBefore('.'))) "document-$cleaned" else cleaned
        val limit = 255 - extension.length - 1
        var end = 0
        var bytes = 0
        // 按码点截断，避免拆开中文或 emoji。
        while (end < name.length) {
            val codePoint = name.codePointAt(end)
            val size = when {
                codePoint <= 0x7F -> 1
                codePoint <= 0x7FF -> 2
                codePoint <= 0xFFFF -> 3
                else -> 4
            }
            if (bytes + size > limit) break
            bytes += size
            end += Character.charCount(codePoint)
        }
        return name.substring(0, end).trimEnd('.', '-') + ".$extension"
    }

    private fun removeDeletedDocumentFiles(id: String) {
        if (retainedExportMedia[id].isNullOrEmpty() && !File(root, id).deleteRecursively()) loge("Delete document files failed")
    }

    private suspend fun cleanMedia(id: String) {
        val referenced = dao.referencedFiles(id).toSet()
        val retained = retainedExportMedia[id].orEmpty().keys
        unreferencedMedia(File(root, id), referenced, retained).forEach {
            if (!it.delete()) loge("Delete unreferenced image failed")
        }
    }
}
