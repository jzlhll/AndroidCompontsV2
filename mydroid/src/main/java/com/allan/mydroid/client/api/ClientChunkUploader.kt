package com.allan.mydroid.client.api

import android.net.Uri
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

/** 串行上传分片，合并结果确认后返回实际文件名，失败及取消均执行有界清理。 */
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
    ): String = coroutineScope {
        require(totalSize > 0) { Globals.getString(R.string.transfer_empty_file) }
        val md5 = withIOThread { ClientMd5.streamMd5(uri) }
        val chunkSize = pickChunkSize(totalSize)
        val count = (totalSize - 1) / chunkSize + 1
        require(count <= Int.MAX_VALUE) { "Too many chunks" }
        val totalChunks = count.toInt()
        var merged = false
        try {
            ClientApi.abortUploadChunks(baseUrl, md5, fileName)
            var sentBytes = 0L
            onProgress(0, totalSize)
            for (chunkIndex in 1..totalChunks) {
                ensureActive()
                val offset = (chunkIndex - 1).toLong() * chunkSize
                val remaining = totalSize - offset
                val length = if (remaining < chunkSize) remaining else chunkSize.toLong()
                ClientApi.uploadChunk(baseUrl, uri, fileName, chunkIndex, totalChunks, md5, offset, length)
                sentBytes += length
                onProgress(sentBytes, totalSize)
            }
            ensureActive()
            onMerging()
            // 合并可能已经落盘，页面退出也必须等待结果，避免把成功误判为取消。
            val savedName = withContext(NonCancellable) {
                try {
                    withTimeoutOrNull(120000.milliseconds) {
                        ClientApi.mergeChunks(baseUrl, md5, fileName, totalChunks, lastModified)
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
            savedName
        } finally {
            if (!merged) {
                withContext(NonCancellable) {
                    try {
                        val cleaned = withTimeoutOrNull(5000.milliseconds) {
                            ClientApi.abortUploadChunks(baseUrl, md5, fileName)
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
