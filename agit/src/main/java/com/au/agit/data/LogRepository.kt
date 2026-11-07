package com.au.agit.data

import android.content.Context
import com.au.agit.R
import com.au.agit.appString
import com.au.agit.model.AGitException
import com.au.module_android.log.FileLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.resume

data class LogFileInfo(val name: String, val size: Long, val modifiedAt: Long)
data class LogLine(val id: Long, val original: String, val info: FileLog.LogParserInfo?)
data class LogPage(val lines: List<LogLine>, val olderOffset: Long)

/** 读取模块落盘日志，按需分页；不监听文件变化，也不暂停正常日志写入。 */
class LogRepository(private val context: Context) {
    private val mutex = Mutex()

    private suspend fun awaitWrites() = suspendCancellableCoroutine<Unit> { continuation ->
        FileLog.logHandler.post { if (continuation.isActive) continuation.resume(Unit) }
    }

    private fun resolve(name: String): File {
        if (!name.matches(Regex("[A-Za-z0-9_.-]+\\.log"))) throw AGitException(appString(R.string.log_missing), "LOG_PATH")
        val root = File(FileLog.logDir).canonicalFile
        val file = File(root, name)
        if (Files.isSymbolicLink(file.toPath()) || file.canonicalFile.parentFile != root || !file.isFile) {
            throw AGitException(appString(R.string.log_missing), "LOG_MISSING")
        }
        return file
    }

    suspend fun scan(): List<LogFileInfo> {
        awaitWrites()
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                File(FileLog.logDir).listFiles()?.filter { it.isFile && it.name.endsWith(".log") && !Files.isSymbolicLink(it.toPath()) }
                    ?.map { LogFileInfo(it.name, it.length(), it.lastModified()) }?.sortedByDescending { it.modifiedAt }.orEmpty()
            }
        }
    }

    suspend fun read(name: String, before: Long? = null): LogPage {
        awaitWrites()
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                RandomAccessFile(resolve(name), "r").use { input ->
                    val length = input.length()
                    val end = if (before != null && before < length) before else length
                    val start = if (end > 256 * 1024) end - 256 * 1024 else 0L
                    val bytes = ByteArray((end - start).toInt())
                    input.seek(start)
                    input.readFully(bytes)
                    var skip = 0
                    if (start > 0) {
                        while (skip < bytes.size && bytes[skip] != '\n'.code.toByte()) skip++
                        if (skip == bytes.size) throw AGitException(appString(R.string.log_line_too_long), "LOG_LINE_TOO_LONG")
                        skip++
                    }
                    val text = String(bytes, skip, bytes.size - skip, Charsets.UTF_8)
                    val lines = text.split('\n').let { if (it.lastOrNull().isNullOrEmpty()) it.dropLast(1) else it }
                    LogPage(lines.mapIndexed { index, line ->
                        currentCoroutineContext().ensureActive()
                        val original = line.removeSuffix("\r")
                        LogLine(start + skip + index, original, FileLog.logParser(original))
                    }, start + skip)
                }
            }
        }
    }

    suspend fun delete(name: String) {
        awaitWrites()
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (!resolve(name).delete()) throw AGitException(appString(R.string.log_delete_failed), "LOG_DELETE")
            }
        }
    }

    suspend fun export(names: Set<String>): File {
        if (names.isEmpty() || names.size > 3) throw AGitException(appString(R.string.log_select_limit), "LOG_SELECTION")
        awaitWrites()
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val directory = File(context.cacheDir, "shared/logs").apply { mkdirs() }
                val target = File(directory, "logs-${UUID.randomUUID()}.zip")
                try {
                    ZipOutputStream(target.outputStream().buffered()).use { output ->
                        names.forEach { name ->
                            output.putNextEntry(ZipEntry(name))
                            RandomAccessFile(resolve(name), "r").use { input ->
                                var remaining = input.length()
                                val buffer = ByteArray(65536)
                                while (remaining > 0) {
                                    currentCoroutineContext().ensureActive()
                                    val count = input.read(buffer, 0, if (remaining < buffer.size) remaining.toInt() else buffer.size)
                                    if (count < 0) break
                                    output.write(buffer, 0, count)
                                    remaining -= count
                                }
                            }
                            output.closeEntry()
                        }
                    }
                    target
                } catch (error: Exception) { target.delete(); throw error }
            }
        }
    }
}
