package com.allan.picnote.data

import android.content.Context
import coil3.SingletonImageLoader
import com.allan.picnote.logEx
import com.au.module_android.Globals
import com.bumptech.glide.Glide
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import kotlin.time.Duration.Companion.days

data class CleanupResult(val mirrorFailures: Set<String> = emptySet(), val failedTasks: Set<String> = emptySet())

const val DocumentsDirectory = "documents"
const val ImageDraftsDirectory = "image_drafts"
const val ExportDraftsDirectory = "export_drafts"
const val SharedExportsDirectory = "shared/exports"

fun unreferencedMedia(documentDirectory: File, referenced: Set<String>, retained: Set<String>): List<File> =
    File(documentDirectory, "media").listFiles().orEmpty().filter { it.name !in referenced && it.name !in retained }

/** 复用应用后台作用域串行维护缓存与存储残留，各项失败互不影响。 */
class PicNoteCleaner(
    private val context: Context,
    database: PicNoteDatabase,
    private val storageMutex: Mutex,
    private val activeDrafts: Set<String>,
    private val activeExports: Set<String>,
    private val retainedExportMedia: Map<String, Map<String, Int>>,
    private val writeMirror: suspend (String) -> Boolean,
) {
    private val dao = database.dao()
    private val root = File(context.filesDir, DocumentsDirectory)
    private val draftRoot = File(context.filesDir, ImageDraftsDirectory)
    private val exportDraftRoot = File(context.cacheDir, ExportDraftsDirectory)
    private val bluetoothArchiveRoot = File(context.filesDir, BluetoothArchiveDirectory)
    private val exportRoot = File(context.cacheDir, SharedExportsDirectory)

    suspend fun clean(clearAllCache: Boolean): CleanupResult = withContext(Globals.backgroundScope.coroutineContext.minusKey(Job)) {
        // 与导入、保存和导出快照共用同一把锁。
        storageMutex.withLock {
            val failures = mutableSetOf<String>()
            val mirrorFailures = mutableSetOf<String>()
            // Send 与 Receive 均等待启动清理，设置页清理期间可能已有活跃蓝牙会话。
            if (!clearAllCache) {
                bluetoothArchiveRoot.listFiles().orEmpty().forEach { file ->
                    ignoreCleanupError("bluetooth_archive:${file.name}", failures) { removeFile(file) }
                }
            }
            ignoreCleanupError("documents", failures) {
                val summaries = dao.documents()
                val documents = summaries.map { it.id }.toSet()
                root.listFiles().orEmpty().forEach { directory ->
                    ignoreCleanupError("document:${directory.name}", failures) {
                        val retained = retainedExportMedia[directory.name].orEmpty()
                        if (directory.name !in documents) {
                            if (retained.isEmpty()) removeFile(directory)
                        } else {
                            val referenced = dao.referencedFiles(directory.name).toSet()
                            unreferencedMedia(directory, referenced, retained.keys).forEach { file ->
                                ignoreCleanupError("media:${directory.name}/${file.name}", failures) { removeFile(file) }
                            }
                            removeFile(File(directory, ".doc.md.tmp"))
                        }
                    }
                }
                summaries.forEach { document ->
                    ignoreCleanupError("mirror:${document.id}", failures) {
                        val mirror = File(root, "${document.id}/doc.md")
                        if ((!mirror.isFile || mirror.lastModified() < document.updatedAt) && !writeMirror(document.id)) {
                            mirrorFailures += document.id
                        }
                    }
                }
            }
            ignoreCleanupError("drafts", failures) {
                draftRoot.listFiles().orEmpty().filter { it.name !in activeDrafts }.forEach { file ->
                    ignoreCleanupError("draft:${file.name}", failures) { removeFile(file) }
                }
            }
            ignoreCleanupError("export_drafts", failures) {
                exportDraftRoot.listFiles().orEmpty().filter { it.name !in activeExports }.forEach { file ->
                    ignoreCleanupError("export_draft:${file.name}", failures) { removeFile(file) }
                }
            }
            ignoreCleanupError("exports", failures) {
                val expiry = System.currentTimeMillis() - 7.days.inWholeMilliseconds
                cleanExports(exportRoot, clearAllCache, expiry, failures)
            }
            if (clearAllCache) {
                ignoreCleanupError("coil_disk_cache", failures) { SingletonImageLoader.get(context).diskCache?.clear() }
                ignoreCleanupError("glide_disk_cache", failures) { Glide.get(context).clearDiskCache() }
                ignoreCleanupError("coil_memory_cache", failures) { SingletonImageLoader.get(context).memoryCache?.clear() }
                ignoreCleanupError("glide_memory_cache", failures) {
                    // Glide 要求在主线程释放内存缓存，当前清理任务等待完成后才继续。
                    withContext(Dispatchers.Main) { Glide.get(context).clearMemory() }
                }
            }
            CleanupResult(mirrorFailures, failures)
        }
    }

    private suspend fun cleanExports(directory: File, clearAll: Boolean, expiry: Long, failures: MutableSet<String>) {
        directory.listFiles().orEmpty().forEach { file ->
            ignoreCleanupError("export:${file.relativeTo(exportRoot).path}", failures) {
                if (file.isDirectory && !Files.isSymbolicLink(file.toPath())) {
                    cleanExports(file, clearAll, expiry, failures)
                    if (file.list()?.isEmpty() == true) removeFile(file)
                } else if (clearAll || file.name.endsWith(".tmp") || file.lastModified() < expiry) {
                    Files.deleteIfExists(file.toPath())
                }
            }
        }
    }

    private suspend fun ignoreCleanupError(task: String, failures: MutableSet<String>, block: suspend () -> Unit) {
        currentCoroutineContext().ensureActive()
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failures += task
            logEx(throwable = e, message = "Cleanup task failed task=$task")
        }
    }

    private fun removeFile(file: File) {
        check(!file.exists() || file.deleteRecursively()) { "Cannot delete file: ${file.absolutePath}" }
    }
}
