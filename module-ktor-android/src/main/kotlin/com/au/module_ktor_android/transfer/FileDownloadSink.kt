package com.au.module_ktor_android.transfer

import com.au.module_ktor_base.client.KtorRequestExecutor
import com.au.module_ktor_base.config.RequestOptions
import com.au.module_ktor_base.transfer.DownloadSink
import com.au.module_ktor_base.transfer.storageOperation
import io.ktor.http.Url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap

private val downloadClaims = ConcurrentHashMap<Path, Any>()

/** 同目标并发直接拒绝；失败只删除本次临时文件，不触碰已存在的最终文件。 */
class FileDownloadSink(
    private val target: File,
    private val replaceExisting: Boolean = false,
    private val allowNonAtomicCommit: Boolean = false,
    private val keepPartialFile: Boolean = false,
) : DownloadSink {
    private val claim = Any()
    private var destination: Path? = null
    private var temporary: Path? = null
    private var output: OutputStream? = null
    private var completed = false
    private var aborted = false

    override suspend fun write(buffer: ByteArray, offset: Int, length: Int) = withContext(Dispatchers.IO) {
        storageOperation {
            check(!completed && !aborted) { "Download sink has ended" }
            open()
            output!!.write(buffer, offset, length)
        }
    }

    override suspend fun complete() = withContext(Dispatchers.IO) {
        storageOperation {
            check(!completed && !aborted) { "Download sink has ended" }
            open()
            output!!.close()
            output = null
            currentCoroutineContext().ensureActive()
            val source = temporary!!
            val final = destination!!
            if (replaceExisting) {
                try {
                    Files.move(source, final, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (error: AtomicMoveNotSupportedException) {
                    if (!allowNonAtomicCommit) throw error
                    Files.move(source, final, StandardCopyOption.REPLACE_EXISTING)
                }
            } else {
                // 硬链接原子发布且不会覆盖外部刚创建的目标；默认不退回可能覆盖的 ATOMIC_MOVE。
                try {
                    Files.createLink(final, source)
                } catch (error: UnsupportedOperationException) {
                    if (!allowNonAtomicCommit) throw error
                    Files.move(source, final)
                } catch (error: java.nio.file.FileSystemException) {
                    if (!allowNonAtomicCommit || Files.exists(final, LinkOption.NOFOLLOW_LINKS)) throw error
                    Files.move(source, final)
                }
                Files.deleteIfExists(source)
            }
            temporary = null
            completed = true
            downloadClaims.remove(final, claim)
            Unit
        }
    }

    override suspend fun abort(cause: Throwable) = withContext(Dispatchers.IO) {
        storageOperation {
            if (!completed && !aborted) {
                aborted = true
                try { output?.close() } finally {
                    output = null
                    try {
                        if (!keepPartialFile) temporary?.let { Files.deleteIfExists(it) }
                    } finally { destination?.let { downloadClaims.remove(it, claim) } }
                }
            }
        }
    }

    private fun open() {
        if (output != null) return
        val final = target.canonicalFile.toPath()
        if (destination == null) {
            check(downloadClaims.putIfAbsent(final, claim) == null) { "Download target is in use" }
            destination = final
        }
        if (!replaceExisting && Files.exists(final, LinkOption.NOFOLLOW_LINKS)) throw java.nio.file.FileAlreadyExistsException(final.toString())
        val parent = final.parent ?: throw IllegalArgumentException("Download target has no parent")
        Files.createDirectories(parent)
        val temporaryFile = Files.createTempFile(parent, ".ktor-", ".part")
        temporary = temporaryFile
        output = Files.newOutputStream(temporaryFile, StandardOpenOption.WRITE).buffered()
    }
}

suspend fun KtorRequestExecutor.downloadToFile(
    url: Url,
    target: File,
    options: RequestOptions = RequestOptions(),
    replaceExisting: Boolean = false,
    allowNonAtomicCommit: Boolean = false,
): File {
    downloadUrl(url, FileDownloadSink(target, replaceExisting, allowNonAtomicCommit), options)
    return target
}
