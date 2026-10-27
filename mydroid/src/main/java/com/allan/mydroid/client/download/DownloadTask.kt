package com.allan.mydroid.client.download

import java.util.UUID

enum class DownloadState { Pending, Running, Canceling, Completed, Failed, Canceled }

/** 每次下载尝试独立标识，按主机地址及 HTTP 端口隔离；完成前不占用正式文件。 */
data class DownloadTask(
    val ip: String,
    val httpPort: Int,
    val uriUuid: String,
    val name: String,
    val fileSize: Long,
    val url: String,
    val state: DownloadState = DownloadState.Pending,
    val receivedBytes: Long = 0,
    val error: String? = null,
    val attemptId: String = UUID.randomUUID().toString(),
    val bytesPerSecond: Long = 0
) {
    val hostKey get() = "$ip:$httpPort"
    val active get() = state == DownloadState.Pending || state == DownloadState.Running || state == DownloadState.Canceling
    val progress: Float get() {
        if (fileSize <= 0) return 0f
        val fraction = receivedBytes.toFloat() / fileSize
        return if (fraction > 1f) 1f else fraction
    }
}
