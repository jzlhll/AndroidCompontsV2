package com.allan.mydroid.client.download

import androidx.annotation.Keep
import java.io.File
import java.util.UUID

@Keep
enum class DownloadState { Pending, Running, Canceling, Completed, Failed, Canceled, Missing }

/** 每次下载尝试独立标识，按主机地址及 HTTP 端口隔离；完成前不占用正式文件。 */
@Keep
data class DownloadTask(
    val ip: String,
    val httpPort: Int,
    val uriUuid: String,
    val name: String,
    val fileSizeStr: String,
    val fileSize: Long,
    val mimeType: String,
    val url: String,
    val destFilePath: String = "",
    val state: DownloadState = DownloadState.Pending,
    val receivedBytes: Long = 0,
    val error: String? = null,
    val attemptId: String = UUID.randomUUID().toString(),
    @Transient val bytesPerSecond: Long = 0
) {
    val hostKey get() = "$ip:$httpPort"
    val destFile get() = File(destFilePath)
    val active get() = state == DownloadState.Pending || state == DownloadState.Running || state == DownloadState.Canceling
    val progress: Float get() {
        if (fileSize <= 0) return 0f
        val fraction = receivedBytes.toFloat() / fileSize
        return if (fraction > 1f) 1f else fraction
    }
}
