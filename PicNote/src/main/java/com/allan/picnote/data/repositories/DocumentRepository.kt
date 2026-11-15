package com.allan.picnote.data.repositories

import android.content.Context
import android.content.ContentUris
import android.provider.MediaStore
import androidx.room.withTransaction
import com.allan.picnote.data.PicNoteDatabase
import com.allan.picnote.data.entity.*
import com.allan.picnote.data.markdown
import com.allan.picnote.logEx
import com.allan.picnote.loge
import com.allan.picnote.media.ImagePreparation
import com.allan.picnote.media.PreparedImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class Saved<T>(val value: T, val mirrorFailed: Boolean = false)
data class ImageDraft(val existing: NodeImageEntity? = null, val prepared: PreparedImage? = null, val name: String = "", val id: String = existing?.id ?: UUID.randomUUID().toString())
data class HomeDocuments(val documents: List<DocumentSummary>, val content: DocumentContent?)

/** 串行维护文档结构、WebP 副本、源 URI 映射与可重建的 Markdown 镜像。 */
class DocumentRepository(private val context: Context, private val database: PicNoteDatabase) {
    private val dao = database.dao()
    private val mutex = Mutex()
    private val root = File(context.filesDir, "documents")
    private val draftRoot = File(context.filesDir, "image_drafts")
    private val activeDrafts = ConcurrentHashMap.newKeySet<String>()
    private val preparation = ImagePreparation(context)
    private val preferences = context.getSharedPreferences("picnote", Context.MODE_PRIVATE)

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
        check(session in activeDrafts) { "图片草稿已关闭，请重新打开编辑器" }
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
        val document = dao.document(id) ?: error("文档已删除，请重新选择")
        val images = dao.images(id).groupBy { it.nodeId }
        return DocumentContent(document, dao.nodes(id).map { NodeContent(it, images[it.id].orEmpty()) })
    }

    suspend fun create(title: String, firstBody: String): Saved<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(title.trim().isNotEmpty()) { "请填写标题" }
            val id = UUID.randomUUID().toString()
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) {
                database.withTransaction {
                    dao.putDocument(DocumentEntity(id, title.trim(), System.currentTimeMillis()))
                    if (firstBody.isNotBlank()) dao.putNodes(listOf(NodeEntity(UUID.randomUUID().toString(), id, 1024, "introduction", firstBody)))
                }
                preferences.edit().putString("last_document", id).apply()
                Saved(id, !writeMirror(id))
            }
        }
    }

    suspend fun rename(id: String, title: String): Saved<Unit> = mutate(id) {
        require(title.trim().isNotEmpty()) { "请填写标题" }
        dao.renameDocument(id, title.trim(), System.currentTimeMillis())
    }

    suspend fun deleteDocument(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            database.withTransaction { dao.deleteDocument(id) }
            if (!File(root, id).deleteRecursively()) loge("Delete document files failed")
        }
    }

    suspend fun deleteNode(documentId: String, nodeId: String): Saved<Unit> = mutate(documentId) {
        require(dao.nodes(documentId).any { it.id == nodeId }) { "节点已删除" }
        dao.deleteNode(nodeId)
    }

    suspend fun move(documentId: String, nodeId: String, beforeId: String?): Saved<Unit> = mutate(documentId) {
        val nodes = dao.nodes(documentId).toMutableList()
        val node = nodes.firstOrNull { it.id == nodeId } ?: error("节点已删除")
        if (beforeId == nodeId) return@mutate
        nodes.remove(node)
        val index = if (beforeId == null) nodes.size else nodes.indexOfFirst { it.id == beforeId }
        require(index >= 0) { "排序位置已失效，请重试" }
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

    suspend fun saveNode(
        documentId: String,
        nodeId: String?,
        anchorId: String?,
        after: Boolean,
        chapter: Boolean,
        title: String,
        body: String,
        images: List<ImageDraft>,
    ): Saved<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val sourceVersions = dao.scanStates().associate { it.volume to it.version }
            val original = snapshot(documentId)
            if (nodeId != null) require(original.nodes.any { it.node.id == nodeId }) { "节点已删除，请返回重试" }
            val introduction = original.nodes.firstOrNull { it.node.id == nodeId }?.node?.kind == "introduction"
            require(!introduction || (!chapter && images.isEmpty())) { "引言不能添加图片或改为章节" }
            if (anchorId != null) require(original.nodes.any { it.node.id == anchorId }) { "插入位置已失效，请重新选择" }
            require(!chapter || title.trim().isNotEmpty()) { "请填写章节标题" }
            val createdFiles = mutableListOf<File>()
            var committed = false
            try {
                val id = nodeId ?: UUID.randomUUID().toString()
                val byHash = original.nodes.filter { it.node.id != nodeId }.flatMap { it.images }.associateBy { it.hash }.toMutableMap()
                val savedImages = mutableListOf<NodeImageEntity>()
                val mappings = mutableListOf<SourceImageEntity>()
                val consumedUris = mutableSetOf<String>()
                if (!chapter) images.forEach { draft ->
                    currentCoroutineContext().ensureActive()
                    val existing = draft.existing
                    if (existing != null) {
                        val current = original.nodes.firstOrNull { it.node.id == nodeId }?.images?.firstOrNull { it.id == existing.id }
                            ?: error("图片已失效")
                        require(current.hash !in byHash) { "这张图片已存在于当前文档，原节点尚未修改。" }
                        val image = current.copy(sortIndex = savedImages.size, name = draft.name)
                        savedImages += image
                        byHash[image.hash] = image
                    } else {
                        val prepared = draft.prepared ?: error("图片尚未准备完成，请重新选择")
                        val duplicate = byHash[prepared.hash]
                        require(nodeId == null || duplicate == null) { "这张图片已存在于当前文档，请选择其他图片。原节点尚未修改。" }
                        val image = duplicate ?: copyPreparedImage(documentId, id, prepared, draft.name, savedImages.size, createdFiles, sourceVersions)
                        if (duplicate == null) {
                            savedImages += image
                            byHash[image.hash] = image
                        }
                        mappings += SourceImageEntity(prepared.sourceUri, documentId, image.fileName)
                        sourceUri(prepared, sourceVersions)?.let { consumedUris += it }
                    }
                }
                require(chapter || body.isNotBlank() || savedImages.isNotEmpty()) { "请填写文字或添加尚未导入的图片" }
                currentCoroutineContext().ensureActive()
                withContext(NonCancellable) {
                    database.withTransaction {
                        require(dao.document(documentId) != null) { "文档已删除" }
                        val currentNodes = dao.nodes(documentId)
                        if (anchorId != null) require(currentNodes.any { it.id == anchorId }) { "插入位置已失效，请重新选择" }
                        val kind = when { introduction -> "introduction"; chapter -> "chapter"; savedImages.isEmpty() -> "text"; else -> "image" }
                        val validSources = validSourceUris(consumedUris + savedImages.mapNotNull { it.sourceUri }, sourceVersions)
                        if (nodeId == null) {
                            dao.putNodes(listOf(NodeEntity(id, documentId, 0, kind, body, title.trim())))
                            val order = currentNodes.map { it.id }.toMutableList()
                            val index = if (anchorId == null) order.size else order.indexOf(anchorId) + if (after) 1 else 0
                            order.add(index, id)
                            placeKey(documentId, order, index)
                        } else {
                            dao.updateNode(id, kind, body, title.trim())
                            dao.deleteImages(id)
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
                        val image = duplicate ?: copyPreparedImage(documentId, nodeId, source, "", 0, createdFiles, sourceVersions)
                        mappings += SourceImageEntity(source.sourceUri, documentId, image.fileName)
                        sourceUri(source, sourceVersions)?.let { consumedUris += it }
                        if (duplicate == null) {
                            nodes += NodeEntity(nodeId, documentId, 0, "image")
                            images += image
                            byHash[image.hash] = image
                        }
                    }
                    currentCoroutineContext().ensureActive()
                    withContext(NonCancellable) {
                        database.withTransaction {
                            require(dao.document(documentId) != null) { "文档已删除，请重新选择" }
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

    private suspend fun copyPreparedImage(documentId: String, nodeId: String, image: PreparedImage, name: String, index: Int,
        createdFiles: MutableList<File>, versions: Map<String, String>): NodeImageEntity {
        require(image.file.isFile && image.file.canRead()) { "图片草稿不可读，请重新选择" }
        val directory = File(root, "$documentId/media").apply { check(isDirectory || mkdirs()) { "无法创建图片目录" } }
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
            image.width, image.height, name, sourceUri(image, versions))
    }

    private suspend fun <T> mutate(id: String, block: suspend () -> T): Saved<T> = withContext(Dispatchers.IO) {
        mutex.withLock {
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) {
                val value = database.withTransaction {
                    require(dao.document(id) != null) { "文档已删除" }
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

    suspend fun export(id: String): File = withContext(Dispatchers.IO) {
        mutex.withLock {
            val content = snapshot(id)
            val directory = File(context.cacheDir, "exports").apply { mkdirs() }
            val output = File(directory, "picnote-${UUID.randomUUID()}.zip")
            val temporary = File(directory, ".${output.name}.tmp")
            try {
                ZipOutputStream(FileOutputStream(temporary)).use { zip ->
                    zip.putNextEntry(ZipEntry("document.md"))
                    zip.write(content.markdown().toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                    content.nodes.flatMap { it.images }.distinctBy { it.fileName }.forEach { image ->
                        currentCoroutineContext().ensureActive()
                        val file = imageFile(id, image.fileName)
                        require(file.isFile && file.canRead()) { "图片不可读，无法导出完整文档" }
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

    suspend fun cleanup(): Set<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val summaries = dao.documents()
            val documents = summaries.map { it.id }.toSet()
            root.listFiles()?.forEach { directory ->
                if (directory.name !in documents) directory.deleteRecursively()
                else {
                    cleanMedia(directory.name)
                    File(directory, ".doc.md.tmp").delete()
                }
            }
            draftRoot.listFiles()?.filter { it.name !in activeDrafts }?.forEach { it.deleteRecursively() }
            val expiry = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
            File(context.cacheDir, "exports").listFiles()?.filter { it.name.endsWith(".tmp") || it.lastModified() < expiry }?.forEach { it.delete() }
            val failures = mutableSetOf<String>()
            summaries.forEach { document ->
                val mirror = File(root, "${document.id}/doc.md")
                if ((!mirror.isFile || mirror.lastModified() < document.updatedAt) && !writeMirror(document.id)) failures += document.id
            }
            failures
        }
    }

    private suspend fun cleanMedia(id: String) {
        val referenced = dao.referencedFiles(id).toSet()
        File(root, "$id/media").listFiles()?.filter { it.name !in referenced }?.forEach {
            if (!it.delete()) loge("Delete unreferenced image failed")
        }
    }
}
