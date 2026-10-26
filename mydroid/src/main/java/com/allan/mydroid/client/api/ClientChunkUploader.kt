package com.allan.mydroid.client.api

import android.net.Uri
import java.security.MessageDigest
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import java.util.UUID
import com.allan.mydroid.R
import com.au.module_android.Globals
import com.au.module_android.log.logEx
import com.au.module_android.utils.withIOThread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

data class UploadedFileResult(val fileName: String, val md5: String)

/** 顺序读取文件并同时发送、计算摘要，合并校验成功后才返回完整摘要。 */
class ClientChunkUploader {
    suspend fun upload(
        baseUrl: String,
        uri: Uri,
        fileName: String,
        totalSize: Long,
        lastModified: Long,
        onMerging: () -> Unit,
        onCleanupFailed: () -> Unit,
        onProgress: (sentBytes: Long, totalBytes: Long) -> Unit
    ): UploadedFileResult = coroutineScope {
        require(totalSize > 0) { Globals.getString(R.string.transfer_empty_file) }
        val digest = MessageDigest.getInstance("MD5")
        val chunkSize = pickChunkSize(totalSize)
        val count = (totalSize - 1) / chunkSize + 1
        require(count <= Int.MAX_VALUE) { "Too many chunks" }
        val totalChunks = count.toInt()
        val uploadId = UUID.randomUUID().toString()
        var merged = false
        var input: InputStream? = null
        try {
            withIOThread {
                input = Globals.app.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open selected file")
            }
            val source = checkNotNull(input)
            var sentBytes = 0L
            onProgress(0, totalSize)
            for (chunkIndex in 1..totalChunks) {
                ensureActive()
                val remaining = totalSize - sentBytes
                val length = if (remaining < chunkSize) remaining.toInt() else chunkSize
                val bytes = withIOThread {
                    val buffer = ByteArray(length)
                    var offset = 0
                    while (offset < buffer.size) {
                        currentCoroutineContext().ensureActive()
                        val count = source.read(buffer, offset, buffer.size - offset)
                        if (count == -1) throw EOFException("Selected file became shorter")
                        if (count > 0) offset += count
                    }
                    buffer
                }
                coroutineScope {
                    val hashJob = async(Dispatchers.Default) { digest.update(bytes) }
                    ClientApi.uploadChunk(baseUrl, bytes, fileName, chunkIndex, totalChunks, uploadId)
                    hashJob.await()
                }
                sentBytes += length
                onProgress(sentBytes, totalSize)
            }
            ensureActive()
            withIOThread { check(source.read() == -1) { "Selected file became longer" } }
            val md5 = digest.digest().joinToString("") { "%02x".format(it) }
            onMerging()
            // 合并可能已经落盘，页面退出也必须等待结果，避免把成功误判为取消。
            val savedName = withContext(NonCancellable) {
                try {
                    withTimeoutOrNull(120000.milliseconds) {
                        ClientApi.mergeChunks(baseUrl, md5, fileName, totalChunks, lastModified, uploadId, "stream")
                    } ?: throw IllegalStateException(Globals.getString(R.string.transfer_merge_uncertain))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ClientApi.ClientApiException) {
                    throw e
                } catch (e: Exception) {
                    throw IllegalStateException(Globals.getString(R.string.transfer_merge_uncertain), e)
                }
            }
            merged = true
            UploadedFileResult(savedName, md5)
        } finally {
            withContext(NonCancellable) {
                try { withIOThread { input?.close() } }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { logEx(throwable = e) { "Close upload source failed" } }
            }
            if (!merged) {
                withContext(NonCancellable) {
                    try {
                        val cleaned = withTimeoutOrNull(5000.milliseconds) {
                            ClientApi.abortUploadChunks(baseUrl, "", fileName, uploadId, "stream")
                            true
                        } == true
                        if (!cleaned) onCleanupFailed()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logEx(throwable = e) { "Upload cleanup failed" }
                        onCleanupFailed()
                    }
                }
            }
        }
    }

    private fun pickChunkSize(totalSize: Long): Int = when {
        totalSize <= 10L * 1024 * 1024 -> 512 * 1024
        totalSize <= 100L * 1024 * 1024 -> 3 * 1024 * 1024
        totalSize <= 500L * 1024 * 1024 -> 4 * 1024 * 1024
        else -> 5 * 1024 * 1024
    }
}
