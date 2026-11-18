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
import kotlin.time.Duration.Companion.days

data class CleanupResult(val mirrorFailures: Set<String> = emptySet(), val failedTasks: Set<String> = emptySet())

/** 复用应用后台作用域串行维护缓存与存储残留，各项失败互不影响。 */
class PicNoteCleaner(
    private val context: Context,
    database: PicNoteDatabase,
    private val storageMutex: Mutex,
    private val activeDrafts: Set<String>,
    private val writeMirror: suspend (String) -> Boolean,
) {
    private val dao = database.dao()
    private val root = File(context.filesDir, "documents")
    private val draftRoot = File(context.filesDir, "image_drafts")
    private val bluetoothArchiveRoot = File(context.filesDir, BluetoothArchiveDirectory)
    private val exportRoot = File(context.cacheDir, "shared/exports")

    suspend fun clean(clearAllCache: Boolean): CleanupResult = withContext(Globals.backgroundScope.coroutineContext.minusKey(Job)) {
        // 与导入、保存和导出共用同一把锁，清理之间也不会交错执行。
        storageMutex.withLock {
            val failures = mutableSetOf<String>()
            val mirrorFailures = mutableSetOf<String>()
            ignoreCleanupError("documents", failures) {
                val summaries = dao.documents()
                val documents = summaries.map { it.id }.toSet()
                root.listFiles().orEmpty().forEach { directory ->
                    ignoreCleanupError("document:${directory.name}", failures) {
                        if (directory.name !in documents) removeFile(directory)
                        else {
                            val referenced = dao.referencedFiles(directory.name).toSet()
                            File(directory, "media").listFiles().orEmpty().filter { it.name !in referenced }.forEach { file ->
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
            ignoreCleanupError("exports", failures) {
                val expiry = System.currentTimeMillis() - 7.days.inWholeMilliseconds
                exportRoot.listFiles().orEmpty()
                    .filter { clearAllCache || it.name.endsWith(".tmp") || it.lastModified() < expiry }
                    .forEach { file ->
                        ignoreCleanupError("export:${file.name}", failures) { removeFile(file) }
                    }
            }
            ignoreCleanupError("bluetooth_archives", failures) {
                val expiry = System.currentTimeMillis() - 1.days.inWholeMilliseconds
                bluetoothArchiveRoot.listFiles().orEmpty().filter { it.lastModified() < expiry }.forEach { file ->
                    ignoreCleanupError("bluetooth_archive:${file.name}", failures) { removeFile(file) }
                }
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
