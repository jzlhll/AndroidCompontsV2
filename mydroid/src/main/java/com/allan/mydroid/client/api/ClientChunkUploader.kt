package com.allan.mydroid.client.api

import android.net.Uri
import com.au.module_android.log.logdNoFile
import com.au.module_android.utils.withIOThread
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlin.math.min

/**
 * client 端分片上传器。Koin single。照搬 mydroid-send-to-phone.js:81-92 的分片大小算法。
 *
 * - 串行上传分片避免 host 端 chunkIndex 乱序。
 * - 通过 coroutineContext ensureActive 支持取消，外部 cancel 后停止循环并 abort。
 * - 失败时调 /abort-upload-chunks 清理 host 已收分片。
 */
class ClientChunkUploader {
    /**
     * 上传一个 Uri 文件到 host。完成后调 /merge-chunks。
     *
     * @param onProgress (sentBytes, totalBytes) 进度回调，主线程
     */
    suspend fun upload(
        baseUrl: String,
        uri: Uri,
        fileName: String,
        totalSize: Long,
        onProgress: (sentBytes: Long, totalBytes: Long) -> Unit
    ) = coroutineScope {
        val md5 = withIOThread { ClientMd5.streamMd5(uri) }
        val chunkSize = pickChunkSize(totalSize)
        val totalChunks = if (chunkSize > 0) ((totalSize + chunkSize - 1) / chunkSize).toInt() else 1
        logdNoFile { "upload $fileName size=$totalSize chunkSize=$chunkSize totalChunks=$totalChunks md5=$md5" }

        var sentBytes = 0L
        try {
            for (chunkIndex in 0 until totalChunks) {
                ensureActive()
                val offset = chunkIndex.toLong() * chunkSize
                val length = min(chunkSize.toLong(), totalSize - offset)
                ClientApi.uploadChunk(
                    baseUrl = baseUrl,
                    uri = uri,
                    fileName = fileName,
                    chunkIndex = chunkIndex,
                    totalChunks = totalChunks,
                    md5 = md5,
                    offset = offset,
                    length = length
                )
                sentBytes += length
                onProgress(sentBytes, totalSize)
            }

            ensureActive()
            ClientApi.mergeChunks(
                baseUrl = baseUrl,
                md5 = md5,
                fileName = fileName,
                totalChunks = totalChunks,
                lastModified = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            logdNoFile { "upload $fileName failed, abort: ${e.message}" }
            runCatching {
                ClientApi.abortUploadChunks(baseUrl, md5, fileName)
            }
            throw e
        }
    }

    private fun pickChunkSize(totalSize: Long): Int {
        val tenMb = 10L * 1024 * 1024
        val hundredMb = 100L * 1024 * 1024
        val fiveHundredMb = 500L * 1024 * 1024
        return when {
            totalSize <= tenMb -> 512 * 1024
            totalSize <= hundredMb -> 3 * 1024 * 1024
            totalSize <= fiveHundredMb -> 4 * 1024 * 1024
            else -> 5 * 1024 * 1024
        }
    }
}
