package com.allan.picnote.data.repositories

import android.content.Context
import android.content.ContentUris
import android.content.res.Resources
import android.provider.MediaStore
import androidx.room.withTransaction
import com.allan.picnote.data.PicNoteDatabase
import com.allan.picnote.data.WelcomeDocumentId
import com.allan.picnote.data.WelcomeDocumentTemplate
import com.allan.picnote.data.entity.*
import com.allan.picnote.data.markdown
import com.allan.picnote.logEx
import com.allan.picnote.loge
import com.allan.picnote.media.ImagePreparation
import com.allan.picnote.media.PreparedImage
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
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class Saved<T>(val value: T, val mirrorFailed: Boolean = false)
data class ImageDraft(val existing: NodeImageEntity? = null, val prepared: PreparedImage? = null, val id: String = existing?.id ?: UUID.randomUUID().toString())
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

    suspend fun initializeWelcome(): Saved<String>? = withIOThread {
        mutex.withLock {
            if (preferences.getBoolean("welcome_initialized", false)) return@withLock null
            // 已有文档的安装不补插教程，用户删除教程后也不再重建。
            if (dao.documents().isNotEmpty()) {
                check(preferences.edit().putBoolean("welcome_initialized", true).commit()) { "无法保存教程初始化状态" }
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
                check(directory.isDirectory || directory.mkdirs()) { "无法创建教程图片目录" }
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
                        "无法保存教程初始化状态"
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
            require(title.trim().isNotEmpty()) { "请填写标题" }
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

    suspend fun rename(id: String, title: String): Saved<Unit> = mutate(id) {
        require(title.trim().isNotEmpty()) { "请填写标题" }
        val titleNode = dao.nodes(id).first()
        require(titleNode.kind == "title") { "文档标题节点无效" }
        dao.updateNode(titleNode.id, "title", "", title.trim())
        dao.renameDocument(id, title.trim(), System.currentTimeMillis())
    }

    suspend fun deleteDocument(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            database.withTransaction { dao.deleteDocument(id) }
            if (!File(root, id).deleteRecursively()) loge("Delete document files failed")
        }
    }

    suspend fun deleteNode(documentId: String, nodeId: String): Saved<Unit> = mutate(documentId) {
        val index = dao.nodes(documentId).indexOfFirst { it.id == nodeId }
        require(index >= 0) { "节点已删除" }
        require(index >= 2) { "标题和主引言不能删除" }
        dao.deleteNode(nodeId)
    }

    suspend fun move(documentId: String, nodeId: String, beforeId: String?): Saved<Unit> = mutate(documentId) {
        val nodes = dao.nodes(documentId).toMutableList()
        val sourceIndex = nodes.indexOfFirst { it.id == nodeId }
        require(sourceIndex >= 0) { "节点已删除" }
        require(sourceIndex >= 2) { "标题和主引言不能移动" }
        val node = nodes[sourceIndex]
        if (beforeId == nodeId) return@mutate
        nodes.remove(node)
        val index = if (beforeId == null) nodes.size else nodes.indexOfFirst { it.id == beforeId }
        require(index >= 2) { "不能移动到标题或主引言之前" }
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

    suspend fun mergeNodes(documentId: String, firstId: String, secondId: String): Saved<String> = mutate(documentId) {
        require(firstId != secondId) { "请选择两个不同的正文节点" }
        val content = snapshot(documentId)
        val selected = content.nodes.filter { it.node.id == firstId || it.node.id == secondId }
        require(selected.size == 2 && selected.all { it.node.kind == "body" }) { "合并节点已变化，请重新选择两个正文节点" }
        // 以当前文档顺序为准，先转移引用，再删除较后节点，避免级联删除图片。
        val earlier = selected[0]
        val later = selected[1]
        val body = selected.map { it.node.bodyMarkdown }.filter { it.isNotBlank() }.joinToString("\n")
        dao.updateNode(earlier.node.id, "body", body, "")
        dao.putImages((earlier.images + later.images).mapIndexed { index, image ->
            image.copy(nodeId = earlier.node.id, sortIndex = index)
        })
        dao.deleteNode(later.node.id)
        earlier.node.id
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
            if (nodeId != null) require(current != null) { "节点已删除，请返回重试" }
            val savedKind = current?.node?.kind ?: kind
            require(savedKind in listOf("title", "introduction", "chapter", "body")) { "节点类型无效" }
            require(nodeId != null || savedKind != "title") { "文档只能有一个标题" }
            require(current == null || savedKind == kind) { "不能更改节点类型" }
            require(savedKind == "body" || images.isEmpty()) { "此节点不能添加图片" }
            val anchorIndex = if (anchorId == null) original.nodes.size else original.nodes.indexOfFirst { it.node.id == anchorId }
            if (nodeId == null) require(anchorIndex >= 2) { "不能插入到标题或主引言之前" }
            require(savedKind !in listOf("title", "chapter") || title.trim().isNotEmpty()) { "请填写标题" }
            require(savedKind != "introduction" || currentIndex == 1 || body.isNotBlank()) { "请填写引言" }
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
                        val image = current?.images?.firstOrNull { it.id == existing.id } ?: error("图片已失效")
                        require(image.hash !in byHash) { "这张图片已存在于当前文档，原节点尚未修改。" }
                        val saved = image.copy(sortIndex = savedImages.size)
                        savedImages += saved
                        byHash[saved.hash] = saved
                    } else {
                        val prepared = draft.prepared ?: error("图片尚未准备完成，请重新选择")
                        val duplicate = byHash[prepared.hash]
                        require(nodeId == null || duplicate == null) { "这张图片已存在于当前文档，请选择其他图片。原节点尚未修改。" }
                        val image = duplicate ?: copyPreparedImage(documentId, id, prepared, savedImages.size, createdFiles, sourceVersions)
                        if (duplicate == null) {
                            savedImages += image
                            byHash[image.hash] = image
                        }
                        mappings += SourceImageEntity(prepared.sourceUri, documentId, image.fileName)
                        sourceUri(prepared, sourceVersions)?.let { consumedUris += it }
                    }
                }
                require(savedKind != "body" || body.isNotBlank() || savedImages.isNotEmpty()) { "请填写文字或添加尚未导入的图片" }
                currentCoroutineContext().ensureActive()
                withContext(NonCancellable) {
                    database.withTransaction {
                        require(dao.document(documentId) != null) { "文档已删除" }
                        val currentNodes = dao.nodes(documentId)
                        val validSources = validSourceUris(consumedUris + savedImages.mapNotNull { it.sourceUri }, sourceVersions)
                        val savedTitle = if (savedKind == "title" || savedKind == "chapter") title.trim() else ""
                        val savedBody = if (savedKind == "body" || savedKind == "introduction") body else ""
                        if (nodeId == null) {
                            val index = if (anchorId == null) currentNodes.size else currentNodes.indexOfFirst { it.id == anchorId }
                            require(index >= 2) { "插入位置已失效，请重新选择" }
                            dao.putNodes(listOf(NodeEntity(id, documentId, 0, savedKind, savedBody, savedTitle)))
                            val order = currentNodes.map { it.id }.toMutableList()
                            order.add(index, id)
                            placeKey(documentId, order, index)
                        } else {
                            require(currentNodes.any { it.id == nodeId && it.kind == savedKind }) { "节点已变化，请返回重试" }
                            dao.updateNode(id, savedKind, savedBody, savedTitle)
                            dao.deleteImages(id)
                            if (savedKind == "title") dao.renameDocument(documentId, savedTitle, System.currentTimeMillis())
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

    private suspend fun copyPreparedImage(documentId: String, nodeId: String, image: PreparedImage, index: Int,
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
            image.width, image.height, sourceUri(image, versions))
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
            check(exportRoot.isDirectory || exportRoot.mkdirs()) { "无法创建文档导出目录" }
            val output = File(exportRoot, "picnote-${UUID.randomUUID()}.zip")
            val temporary = File(exportRoot, ".${output.name}.tmp")
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
            exportRoot.listFiles()?.filter { it.name.endsWith(".tmp") || it.lastModified() < expiry }?.forEach { it.delete() }
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
