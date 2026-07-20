package com.allan.mydroid.client.download

import androidx.annotation.Keep
import java.io.File
import kotlin.math.min

/** 下载状态。 */
enum class DownloadState { Pending, Running, Completed, Failed, Canceled }

/**
 * 下载任务信息。字段参考 HTML ReceiveFromPhone.html:297-301 (name, fileSizeStr, uriUuid) 并扩展。
 * ip 字段用于 GlobalDownloadObj KV 隔离，避免跨 host 串号。
 * httpPort 字段缓存 host HTTP 端口，给通知点击跳回 ConnectToHostFragment 用。
 * destFilePath 为 [Globals.goodCacheDir]/shared/nanoMerged/ 下的绝对路径，
 * 与 host 端接收文件目录一致，便于下载完后作为主机直接再发送。
 */
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
    val destFilePath: String,
    val state: DownloadState = DownloadState.Pending,
    val receivedBytes: Long = 0,
    val error: String? = null
) {
    val destFile: File get() = File(destFilePath)

    val progress: Float
        get() = if (fileSize > 0) min(1f, receivedBytes.toFloat() / fileSize) else 0f
}

/** 单个 host 的下载任务列表，作为 GlobalDownloadObj KV 的 value。 */
data class HostDownloadList(
    val ip: String,
    val tasks: List<DownloadTask>
)
