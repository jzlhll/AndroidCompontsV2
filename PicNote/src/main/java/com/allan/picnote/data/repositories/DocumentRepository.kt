package com.allan.picnote.data.repositories

import android.content.Context
import android.content.ContentUris
import android.content.res.Resources
import android.provider.MediaStore
import android.util.Base64
import androidx.room.withTransaction
import com.allan.picnote.R
import com.allan.picnote.data.BluetoothArchiveDirectory
import com.allan.picnote.data.BluetoothArchiveException
import com.allan.picnote.data.BluetoothDocumentArchive
import com.allan.picnote.data.PicNoteDatabase
import com.allan.picnote.data.PicNoteCleaner
import com.allan.picnote.data.WelcomeDocumentId
import com.allan.picnote.data.WelcomeDocumentTemplate
import com.allan.picnote.data.bluetoothArchiveStorageFailure
import com.allan.picnote.data.deleteBluetoothArchiveFiles
import com.allan.picnote.data.entity.*
import com.allan.picnote.data.markdown
import com.allan.picnote.data.parseCodeBlock
import com.allan.picnote.logEx
import com.allan.picnote.loge
import com.allan.picnote.media.ImagePreparation
import com.allan.picnote.media.PreparedImage
import com.au.module_android.Globals
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
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.text.Normalizer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class Saved<T>(val value: T, val mirrorFailed: Boolean = false)
data class ImageDraft(val existing: NodeImageEntity? = null, val prepared: PreparedImage? = null, val id: String = existing?.id ?: UUID.randomUUID().toString(), val pendingRemoval: Boolean = false)
data class HomeDocuments(val documents: List<DocumentSummary>, val content: DocumentContent?)

/** 串行维护文档结构、WebP 副本、源 URI 映射与可重建的 Markdown 镜像。 */
class DocumentRepository(private val context: Context, private val database: PicNoteDatabase) {
    private val dao = database.dao()
    private val mutex = Mutex()
    private val root = File(context.filesDir, "documents")
    private val draftRoot = File(context.filesDir, "image_drafts")
    private val exportRoot = File(context.cacheDir, "shared/exports")
    private val activeDrafts = ConcurrentHashMap.newKeySet<String>()
    private val preparation = ImagePreparation(context)
    private val bluetoothArchive = BluetoothDocumentArchive(File(context.filesDir, BluetoothArchiveDirectory))
    private val preferences = context.getSharedPreferences("picnote", Context.MODE_PRIVATE)
    val cleaner = PicNoteCleaner(context, database, mutex, activeDrafts, ::writeMirror)

    fun imageFile(documentId: String, fileName: String): File = File(root, "$documentId/media/$fileName")

    fun documentDirectory(documentId: String): File = File(root, documentId)

    fun openDraftSession(): String = UUID.randomUUID().toString().also { activeDrafts.add(it) }

    suspend fun closeDraftSession(session: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            activeDrafts.remove(session)
            try {
                if (!File(draftRoot, session).deleteRecursively()) loge("Delete image draft files failed")
            } catch (e: Exception) { logEx(throwable = e, message = "Delete image draft files failed") }
        }
    }

    suspend fun prepareImages(session: String, uris: List<String>): List<PreparedImage> = withContext(Dispatchers.IO) {
        mutex.withLock { prepareBatch(session, uris) }
    }

    private suspend fun prepareBatch(session: String, uris: List<String>): List<PreparedImage> {
        check(session in activeDrafts) { Globals.app.getString(R.string.error_draft_closed) }
        val directory = File(draftRoot, session)
        val states = dao.scanStates()
        val prepared = mutableListOf<PreparedImage>()
        try {
            uris.forEach { raw ->
                currentCoroutineContext().ensureActive()
                val source = states.firstOrNull { state ->
                    val collection = MediaStore.Images.Media.getContentUri(state.volume)
                    val prefix = "$collection/"
                    val id = if (raw.startsWith(prefix)) raw.substring(prefix.length).toLongOrNull() else null
                    state.available && id != null && ContentUris.withAppendedId(collection, id).toString() == raw
                }
                prepared += preparation.prepare(raw, directory).copy(queryVolume = source?.volume, queryVersion = source?.version)
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
            val selected = requestedId ?: preferences.getString("last_document", null)
            val id = documents.firstOrNull { it.id == selected }?.id ?: documents.firstOrNull()?.id
            if (id != null) preferences.edit().putString("last_document", id).apply()
            HomeDocuments(documents, id?.let { snapshot(it) })
        }
    }

    suspend fun content(id: String): DocumentContent = withContext(Dispatchers.IO) {
        mutex.withLock { snapshot(id) }
    }

    suspend fun contentIfExists(id: String): DocumentContent? = withContext(Dispatchers.IO) {
        mutex.withLock { if (dao.document(id) == null) null else snapshot(id) }
    }

    private suspend fun snapshot(id: String): DocumentContent {
        val document = dao.document(id) ?: error(Globals.app.getString(R.string.error_document_deleted_reselect))
        return snapshot(document)
    }

    private suspend fun snapshot(document: DocumentEntity): DocumentContent {
        val id = document.id
        val images = dao.images(id).groupBy { it.nodeId }
        return DocumentContent(document, dao.nodes(id).map { NodeContent(it, images[it.id].orEmpty()) })
    }

    suspend fun initializeWelcome(): Saved<String>? = withIOThread {
        mutex.withLock {
            if (preferences.getBoolean("welcome_initialized", false)) return@withLock null
            // 已有文档的安装不补插教程，用户删除教程后也不再重建。
            if (dao.documents().isNotEmpty()) {
                check(preferences.edit().putBoolean("welcome_initialized", true).commit()) { Globals.app.getString(R.string.error_save_welcome_state) }
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
            val image = File(directory, template.imageFileName)
            var committed = false
            try {
                check(directory.isDirectory || directory.mkdirs()) { Globals.app.getString(R.string.error_create_welcome_media_dir) }
                context.assets.open("$assetDirectory/${template.imageFileName}").use { input ->
                    FileOutputStream(image).use { output -> input.copyTo(output); output.fd.sync() }
                }
                currentCoroutineContext().ensureActive()
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
                        dao.putImages(listOf(NodeImageEntity("$id-launcher", imageNodeId, id, 0, template.imageFileName,
                            template.imageSha256, template.imageWidth, template.imageHeight)))
                    }
                    committed = true
                    check(preferences.edit().putBoolean("welcome_initialized", true).putString("last_document", id).commit()) {
                        Globals.app.getString(R.string.error_save_welcome_state)
                    }
                    Saved(id, !writeMirror(id))
                }
            } finally {
                if (!committed) File(root, id).deleteRecursively()
            }
        }
    }

    suspend fun create(title: String, firstBody: String): Saved<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(title.trim().isNotEmpty()) { Globals.app.getString(R.string.error_title_required) }
            val id = UUID.randomUUID().toString()
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) {
                database.withTransaction {
                    dao.putDocument(DocumentEntity(id, title.trim(), System.currentTimeMillis()))
                    dao.putNodes(listOf(
                        NodeEntity(UUID.randomUUID().toString(), id, 1024, "title", title = title.trim()),
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
            if (!File(root, id).deleteRecursively()) loge("Delete document files failed")
        }
    }

    /** 复制整份文档：标题追加 (V2)，节点、图片记录与媒体文件各自独立，镜像按新标题重建。 */
    suspend fun duplicate(id: String): Saved<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val source = snapshot(id)
            val newId = UUID.randomUUID().toString()
            val title = "${source.document.title}(V2)"
            val directory = File(root, newId)
            var committed = false
            try {
                val media = File(root, "$id/media")
                if (media.isDirectory) {
                    val target = File(directory, "media")
                    check(target.isDirectory || target.mkdirs()) { Globals.app.getString(R.string.error_create_image_dir) }
                    media.listFiles()?.filter { it.isFile && !it.name.startsWith(".") }?.forEach { file ->
                        currentCoroutineContext().ensureActive()
                        file.copyTo(File(target, file.name), overwrite = true)
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
                        dao.putSourceImages(images.mapNotNull { image -> image.sourceUri?.let { SourceImageEntity(it, newId, image.fileName) } })
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
        require(index >= 0) { Globals.app.getString(R.string.error_node_deleted) }
        require(index >= 2) { Globals.app.getString(R.string.error_fixed_node_delete) }
        dao.deleteNode(nodeId)
    }

    suspend fun move(documentId: String, nodeId: String, beforeId: String?): Saved<Unit> = mutate(documentId) {
        val nodes = dao.nodes(documentId).toMutableList()
        val sourceIndex = nodes.indexOfFirst { it.id == nodeId }
        require(sourceIndex >= 0) { Globals.app.getString(R.string.error_node_deleted) }
        require(sourceIndex >= 2) { Globals.app.getString(R.string.error_fixed_node_move) }
        val node = nodes[sourceIndex]
        if (beforeId == nodeId) return@mutate
        nodes.remove(node)
        val index = if (beforeId == null) nodes.size else nodes.indexOfFirst { it.id == beforeId }
        require(index >= 2) { Globals.app.getString(R.string.error_move_before_fixed) }
        nodes.add(index, node)
        placeKey(documentId, nodes.map { it.id }, index)
    }

    private suspend fun placeKey(documentId: String, order: List<String>, index: Int) {
        val keys = dao.nodes(documentId).associate { it.id to it.sortKey }
        val previous = order.getOrNull(index - 1)?.let { keys[it] }
        val next = order.getOrNull(index + 1)?.let { keys[it] }
        val key = when {
            previous == null && next == null -> 1024L
            previous == null && next!! > Long.MIN_VALUE + 1024 -> next - 1024
            next == null && previous!! < Long.MAX_VALUE - 1024 -> previous + 1024
            previous != null && next != null && next - previous > 1 -> previous + (next - previous) / 2
            else -> null
        }
        if (key == null) order.forEachIndexed { i, id -> dao.setSortKey(id, (i + 1L) * 1024) }
        else dao.setSortKey(order[index], key)
    }

    suspend fun mergeNodes(documentId: String, nodeIds: List<String>): Saved<String> = mutate(documentId) {
        val ids = nodeIds.toSet()
        require(ids.size >= 2 && ids.size == nodeIds.size) { Globals.app.getString(R.string.error_select_body_nodes) }
        val content = snapshot(documentId)
        val selected = content.nodes.filter { it.node.id in ids }
        require(selected.size == ids.size && selected.all { it.node.kind == "body" }) { Globals.app.getString(R.string.error_merge_nodes_changed) }
        // 以当前文档顺序为准，先转移引用，再删除较后节点，避免级联删除图片。
        val earlier = selected.first()
        val body = selected.map { it.node.bodyMarkdown }.filter { it.isNotBlank() }.joinToString("\n")
        dao.updateNode(earlier.node.id, "body", body, "")
        dao.putImages(selected.flatMap { it.images }.mapIndexed { index, image ->
            image.copy(nodeId = earlier.node.id, sortIndex = index)
        })
        selected.drop(1).forEach { dao.deleteNode(it.node.id) }
        earlier.node.id
    }

    suspend fun moveImage(documentId: String, nodeId: String, imageId: String, direction: Int): Saved<Unit> = mutate(documentId) {
        require(direction == -1 || direction == 1) { Globals.app.getString(R.string.error_invalid_image_direction) }
        val node = snapshot(documentId).nodes.firstOrNull { it.node.id == nodeId } ?: error(Globals.app.getString(R.string.error_node_deleted))
        val index = node.images.indexOfFirst { it.id == imageId }
        require(index >= 0) { Globals.app.getString(R.string.error_image_deleted) }
        val adjacent = node.images.getOrNull(index + direction) ?: error(Globals.app.getString(R.string.error_image_at_edge))
        val image = node.images[index]
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
            val sourceVersions = dao.scanStates().associate { it.volume to it.version }
            val original = snapshot(documentId)
            val currentIndex = original.nodes.indexOfFirst { it.node.id == nodeId }
            val current = original.nodes.getOrNull(currentIndex)
            if (nodeId != null) require(current != null) { Globals.app.getString(R.string.error_node_deleted_retry) }
            val savedKind = current?.node?.kind ?: kind
            require(savedKind in listOf("title", "introduction", "chapter", "body", "code")) { Globals.app.getString(R.string.error_invalid_node_type) }
            require(nodeId != null || savedKind != "title") { Globals.app.getString(R.string.error_only_one_title) }
            require(nodeId != null || savedKind != "introduction") { Globals.app.getString(R.string.error_create_introduction) }
            require(current == null || savedKind == kind) { Globals.app.getString(R.string.error_change_node_type) }
            require(savedKind == "body" || images.isEmpty()) { Globals.app.getString(R.string.error_node_images_not_allowed) }
            val anchorIndex = if (anchorId == null) original.nodes.size else original.nodes.indexOfFirst { it.node.id == anchorId }
            if (nodeId == null) require(anchorIndex >= 2) { Globals.app.getString(R.string.error_insert_before_fixed) }
            require(savedKind !in listOf("title", "chapter") || title.trim().isNotEmpty()) { Globals.app.getString(R.string.error_title_required) }
            require(savedKind != "introduction" || currentIndex == 1 || body.isNotBlank()) { Globals.app.getString(R.string.error_introduction_required) }
            require(savedKind != "code" || parseCodeBlock(body)?.code?.isNotBlank() == true) { Globals.app.getString(R.string.error_code_required) }
            val createdFiles = mutableListOf<File>()
            var committed = false
            try {
                val id = nodeId ?: UUID.randomUUID().toString()
                val byHash = original.nodes.filter { it.node.id != nodeId }.flatMap { it.images }.associateBy { it.hash }.toMutableMap()
                val savedImages = mutableListOf<NodeImageEntity>()
                val mappings = mutableListOf<SourceImageEntity>()
                val consumedUris = mutableSetOf<String>()
                images.forEach { draft ->
                    currentCoroutineContext().ensureActive()
                    val existing = draft.existing
                    if (existing != null) {
                        val image = current?.images?.firstOrNull { it.id == existing.id } ?: error(Globals.app.getString(R.string.error_image_expired))
                        require(image.hash !in byHash) { Globals.app.getString(R.string.error_duplicate_existing_image) }
                        val saved = image.copy(sortIndex = savedImages.size)
                        savedImages += saved
                        byHash[saved.hash] = saved
                    } else {
                        val prepared = draft.prepared ?: error(Globals.app.getString(R.string.error_image_not_ready))
                        val duplicate = byHash[prepared.hash]
                        require(nodeId == null || duplicate == null) { Globals.app.getString(R.string.error_duplicate_image) }
                        val image = duplicate ?: copyPreparedImage(documentId, id, prepared, savedImages.size, createdFiles, sourceVersions)
                        if (duplicate == null) {
                            savedImages += image
                            byHash[image.hash] = image
                        }
                        mappings += SourceImageEntity(prepared.sourceUri, documentId, image.fileName)
                        sourceUri(prepared, sourceVersions)?.let { consumedUris += it }
                    }
                }
                require(savedKind != "body" || body.isNotBlank() || savedImages.isNotEmpty()) { Globals.app.getString(R.string.error_body_required) }
                currentCoroutineContext().ensureActive()
                withContext(NonCancellable) {
                    database.withTransaction {
                        require(dao.document(documentId) != null) { Globals.app.getString(R.string.error_document_deleted) }
                        val currentNodes = dao.nodes(documentId)
                        val validSources = validSourceUris(consumedUris + savedImages.mapNotNull { it.sourceUri }, sourceVersions)
                        val savedTitle = if (savedKind == "title" || savedKind == "chapter") title.trim() else ""
                        val savedBody = if (savedKind in listOf("body", "introduction", "code")) body else ""
                        if (nodeId == null) {
                            val index = if (anchorId == null) currentNodes.size else currentNodes.indexOfFirst { it.id == anchorId }
                            require(index >= 2) { Globals.app.getString(R.string.error_insert_anchor_changed) }
                            dao.putNodes(listOf(NodeEntity(id, documentId, 0, savedKind, savedBody, savedTitle)))
                            val order = currentNodes.map { it.id }.toMutableList()
                            order.add(index, id)
                            placeKey(documentId, order, index)
                        } else {
                            require(currentNodes.any { it.id == nodeId && it.kind == savedKind }) { Globals.app.getString(R.string.error_node_changed_retry) }
                            dao.updateNode(id, savedKind, savedBody, savedTitle)
                            dao.deleteImages(id)
                            if (savedKind == "title") dao.updateDocumentTitle(documentId, savedTitle, System.currentTimeMillis())
                        }
                        dao.putImages(savedImages.map { if (it.sourceUri != null && it.sourceUri !in validSources) it.copy(sourceUri = null) else it })
                        dao.putSourceImages(mappings)
                        dao.cleanSourceImages(documentId)
                        dao.consumePending((consumedUris + savedImages.mapNotNull { it.sourceUri }).filter { it in validSources })
                        dao.touchDocument(documentId, System.currentTimeMillis())
                    }
                    committed = true
                    cleanMedia(documentId)
                    Saved(id, !writeMirror(documentId))
                }
            } finally {
                if (!committed) createdFiles.forEach { it.delete() }
            }
        }
    }

    suspend fun importImages(documentId: String, uris: List<String>, candidateUris: List<String>): Saved<Int> = withContext(Dispatchers.IO) {
        val session = openDraftSession()
        try {
            mutex.withLock {
                val sourceVersions = dao.scanStates().associate { it.volume to it.version }
                val original = snapshot(documentId)
                val byHash = original.nodes.flatMap { it.images }.associateBy { it.hash }.toMutableMap()
                val prepared = prepareBatch(session, uris)
                val createdFiles = mutableListOf<File>()
                var committed = false
                try {
                    val nodes = mutableListOf<NodeEntity>()
                    val images = mutableListOf<NodeImageEntity>()
                    val mappings = mutableListOf<SourceImageEntity>()
                    val consumedUris = candidateUris.toMutableSet()
                    prepared.forEach { source ->
                        currentCoroutineContext().ensureActive()
                        val nodeId = UUID.randomUUID().toString()
                        val duplicate = byHash[source.hash]
                        val image = duplicate ?: copyPreparedImage(documentId, nodeId, source, 0, createdFiles, sourceVersions)
                        mappings += SourceImageEntity(source.sourceUri, documentId, image.fileName)
                        sourceUri(source, sourceVersions)?.let { consumedUris += it }
                        if (duplicate == null) {
                            nodes += NodeEntity(nodeId, documentId, 0, "body")
                            images += image
                            byHash[image.hash] = image
                        }
                    }
                    currentCoroutineContext().ensureActive()
                    withContext(NonCancellable) {
                        database.withTransaction {
                            require(dao.document(documentId) != null) { Globals.app.getString(R.string.error_document_deleted_reselect) }
                            val validSources = validSourceUris(consumedUris, sourceVersions)
                            val current = dao.nodes(documentId)
                            dao.putNodes(nodes)
                            dao.putImages(images.map { if (it.sourceUri != null && it.sourceUri !in validSources) it.copy(sourceUri = null) else it })
                            dao.putSourceImages(mappings)
                            dao.cleanSourceImages(documentId)
                            val order = current.map { it.id }.toMutableList()
                            nodes.forEach { node ->
                                order += node.id
                                placeKey(documentId, order, order.lastIndex)
                            }
                            dao.consumePending(consumedUris.filter { it in validSources })
                            dao.touchDocument(documentId, System.currentTimeMillis())
                        }
                        committed = true
                        cleanMedia(documentId)
                        Saved(nodes.size, !writeMirror(documentId))
                    }
                } finally {
                    if (!committed) createdFiles.forEach { it.delete() }
                }
            }
        } finally { withContext(NonCancellable) { closeDraftSession(session) } }
    }

    private fun sourceUri(image: PreparedImage, versions: Map<String, String>): String? {
        val volume = image.queryVolume ?: return null
        return if (image.queryVersion == versions[volume]) image.sourceUri else null
    }

    private suspend fun validSourceUris(uris: Collection<String>, versions: Map<String, String>): Set<String> {
        val currentVersions = dao.scanStates().associate { it.volume to it.version }
        return uris.filter { uri ->
            versions.any { (volume, version) ->
                uri.startsWith(MediaStore.Images.Media.getContentUri(volume).toString() + "/") && version == currentVersions[volume]
            }
        }.toSet()
    }

    private suspend fun copyPreparedImage(documentId: String, nodeId: String, image: PreparedImage, index: Int,
        createdFiles: MutableList<File>, versions: Map<String, String>): NodeImageEntity {
        require(image.file.isFile && image.file.canRead()) { Globals.app.getString(R.string.error_image_draft_unreadable) }
        val directory = File(root, "$documentId/media").apply { check(isDirectory || mkdirs()) { Globals.app.getString(R.string.error_create_image_dir) } }
        val fileName = "${image.hash}.webp"
        val target = File(directory, fileName)
        if (!target.exists()) {
            val temporary = File(directory, ".${UUID.randomUUID()}.tmp")
            try {
                image.file.inputStream().use { input ->
                    FileOutputStream(temporary).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                        output.fd.sync()
                    }
                }
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
                createdFiles += target
            } finally { temporary.delete() }
        }
        return NodeImageEntity(UUID.randomUUID().toString(), nodeId, documentId, index, fileName, image.hash,
            image.width, image.height, sourceUri(image, versions))
    }

    private suspend fun <T> mutate(id: String, block: suspend () -> T): Saved<T> = withContext(Dispatchers.IO) {
        mutex.withLock {
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) {
                val value = database.withTransaction {
                    require(dao.document(id) != null) { Globals.app.getString(R.string.error_document_deleted) }
                    val result = block()
                    dao.touchDocument(id, System.currentTimeMillis())
                    dao.cleanSourceImages(id)
                    result
                }
                cleanMedia(id)
                Saved(value, !writeMirror(id))
            }
        }
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
    suspend fun exportFull(id: String): File = withContext(Dispatchers.IO) {
        mutex.withLock {
            val content = snapshot(id)
            check(exportRoot.isDirectory || exportRoot.mkdirs()) { Globals.app.getString(R.string.error_create_export_dir) }
            val output = File(exportRoot, "picnote-${UUID.randomUUID()}.zip")
            val temporary = File(exportRoot, ".${output.name}.tmp")
            try {
                ZipOutputStream(FileOutputStream(temporary)).use { zip ->
                    zip.putNextEntry(ZipEntry(exportFileName(content.document.title, "md")))
                    zip.write(content.markdown().toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                    content.nodes.flatMap { it.images }.distinctBy { it.fileName }.forEach { image ->
                        currentCoroutineContext().ensureActive()
                        val file = imageFile(id, image.fileName)
                        require(file.isFile && file.canRead()) { Globals.app.getString(R.string.error_export_image_unreadable) }
                        zip.putNextEntry(ZipEntry("media/${image.fileName}"))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
                Files.move(temporary.toPath(), output.toPath(), StandardCopyOption.ATOMIC_MOVE)
                output
            } finally {
                temporary.delete()
            }
        }
    }

    /** 网页导出：单文件 HTML，图片以 base64 内嵌，直接用浏览器打开即可阅读。 */
    suspend fun exportWeb(id: String): File = withContext(Dispatchers.IO) {
        mutex.withLock {
            val content = snapshot(id)
            check(exportRoot.isDirectory || exportRoot.mkdirs()) { Globals.app.getString(R.string.error_create_export_dir) }
            val output = File(exportRoot, "picnote-${UUID.randomUUID()}.html")
            val temporary = File(exportRoot, ".${output.name}.tmp")
            try {
                FileOutputStream(temporary).use { stream ->
                    val writer = OutputStreamWriter(stream, Charsets.UTF_8)
                    writer.write(htmlHeader(content.document.title))
                    content.nodes.forEach { node ->
                        currentCoroutineContext().ensureActive()
                        when (node.node.kind) {
                            "title" -> writer.write("<h1>${htmlEscape(content.document.title)}</h1>\n")
                            "chapter" -> writer.write("<h2>${htmlEscape(node.node.title)}</h2>\n")
                            else -> {
                                node.images.forEach { image -> writeInlineImage(writer, id, image.fileName) }
                                if (node.node.bodyMarkdown.isNotBlank()) {
                                    val body = htmlRenderer.render(markdownParser.parse(node.node.bodyMarkdown))
                                    if (node.node.kind == "introduction") writer.write("<blockquote>\n$body</blockquote>\n") else writer.write(body)
                                }
                            }
                        }
                    }
                    writer.write("</main></body></html>\n")
                    writer.flush()
                    stream.fd.sync()
                }
                Files.move(temporary.toPath(), output.toPath(), StandardCopyOption.ATOMIC_MOVE)
                output
            } finally {
                temporary.delete()
            }
        }
    }

    private val markdownParser by lazy { Parser.builder().build() }
    private val htmlRenderer by lazy { HtmlRenderer.builder().escapeHtml(true).softbreak("<br>").build() }

    private fun writeInlineImage(writer: Writer, documentId: String, fileName: String) {
        val file = imageFile(documentId, fileName)
        require(file.isFile && file.canRead()) { Globals.app.getString(R.string.error_export_image_unreadable) }
        val mime = when (file.extension.lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            else -> "image/webp"
        }
        val data = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        writer.write("<p><img alt=\"\" src=\"data:$mime;base64,$data\"></p>\n")
    }

    private fun htmlHeader(title: String): String {
        val lang = context.resources.configuration.locales[0].toLanguageTag()
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

    private fun htmlEscape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;")

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

    private suspend fun cleanMedia(id: String) {
        val referenced = dao.referencedFiles(id).toSet()
        File(root, "$id/media").listFiles()?.filter { it.name !in referenced }?.forEach {
            if (!it.delete()) loge("Delete unreferenced image failed")
        }
    }
}
