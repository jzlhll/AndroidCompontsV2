package com.allan.mydroid.client.receive

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allan.mydroid.R
import com.allan.mydroid.client.HostEndpoint
import com.allan.mydroid.client.api.ClientApi
import com.allan.mydroid.client.beans.RemoteFileBean
import com.allan.mydroid.client.download.DownloadState
import com.allan.mydroid.client.download.DownloadTask
import com.allan.mydroid.client.download.GlobalDownloadObj
import com.allan.mydroid.BuildConfig
import com.allan.mydroid.globals.nanoTempCacheMergedDir
import com.au.module_android.Globals
import com.au.module_android.log.loge
import com.au.module_android.utils.launchOnIOThread
import com.au.module_android.utilsmedia.getUriFromFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

/**
 * client 从 host 接收文件模式（对应 host MyDroidMode.Send）。
 *
 * - 不直接做下载，只通过 [GlobalDownloadObj] enqueue 任务，UI 订阅 [GlobalDownloadObj.tasksFlow] 显示进度。
 * - 文件列表走 HTTP /request-file-list。
 * - 下载落盘到 [nanoTempCacheMergedDir]，与 host 端接收文件目录一致，下载完后作为主机可直接再发送。
 */
class ConnectToHostReceiveViewModel(
    private val endpoint: HostEndpoint
) : ViewModel(), KoinComponent {

    private val globalDownloadObj: GlobalDownloadObj by inject()

    private val _uiState = MutableStateFlow(ConnectToHostReceiveUiState())
    val uiState: StateFlow<ConnectToHostReceiveUiState> = _uiState.asStateFlow()

    private val baseUrl: String
        get() = "http://${endpoint.ip}:${endpoint.httpPort}"

    init {
        refreshList()
        observeDownloadTasks()
    }

    private fun observeDownloadTasks() {
        viewModelScope.launch {
            globalDownloadObj.tasksFlow(endpoint.ip).collectLatest { tasks ->
                _uiState.update { it.copy(downloadTasks = tasks) }
            }
        }
    }

    fun refreshList() {
        viewModelScope.launchOnIOThread {
            _uiState.update { it.copy(refreshing = true, error = null) }
            try {
                val files = ClientApi.requestFileList(baseUrl)
                _uiState.update { it.copy(files = files, refreshing = false) }
            } catch (e: Exception) {
                loge { "requestFileList failed: ${e.message}" }
                _uiState.update {
                    it.copy(refreshing = false, error = Globals.getString(R.string.something_error) + ": ${e.message}")
                }
            }
        }
    }

    fun downloadFile(bean: RemoteFileBean) {
        // 已有任务则不重复 enqueue
        val existing = _uiState.value.downloadTasks.find { it.uriUuid == bean.uriUuid }
        if (existing != null && existing.state != DownloadState.Failed && existing.state != DownloadState.Canceled) {
            return
        }
        viewModelScope.launchOnIOThread {
            try {
                val name = bean.name ?: "unknown"
                val mimeType = bean.mimeType ?: guessMime(name)
                val destFilePath = resolveDestFilePath(name) ?: run {
                    _uiState.update { it.copy(error = Globals.getString(R.string.save_failed)) }
                    return@launchOnIOThread
                }
                val url = ClientApi.downloadFileUrl(endpoint.ip, endpoint.httpPort, bean.uriUuid)
                val task = DownloadTask(
                    ip = endpoint.ip,
                    httpPort = endpoint.httpPort,
                    uriUuid = bean.uriUuid,
                    name = name,
                    fileSizeStr = bean.fileSizeStr,
                    fileSize = bean.fileSize ?: 0L,
                    mimeType = mimeType,
                    url = url,
                    destFilePath = destFilePath,
                )
                globalDownloadObj.enqueue(task)
            } catch (e: Exception) {
                loge { "downloadFile prepare failed: ${e.message}" }
                _uiState.update {
                    it.copy(error = Globals.getString(R.string.something_error) + ": ${e.message}")
                }
            }
        }
    }

    fun cancelDownload(uriUuid: String) {
        globalDownloadObj.cancel(endpoint.ip, uriUuid)
    }

    fun retryDownload(uriUuid: String) {
        globalDownloadObj.retry(endpoint.ip, uriUuid)
    }

    fun openDownloadedFile(task: DownloadTask) {
        if (task.state != DownloadState.Completed) return
        try {
            // FileProvider 暴露 goodCacheDir/shared/nanoMerged（file_path.xml 中 cache-path path="./shared"）
            val uri = getUriFromFile(Globals.app, "${BuildConfig.APPLICATION_ID}.fileprovider", task.destFile)
                ?: throw IllegalStateException("FileProvider uri is null")
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, task.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            Globals.app.startActivity(Intent.createChooser(intent, Globals.getString(R.string.open)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            loge { "openDownloadedFile failed: ${e.message}" }
            _uiState.update { it.copy(error = Globals.getString(R.string.open_with) + ": ${e.message}") }
        }
    }

    fun consumeError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * 在 [nanoTempCacheMergedDir] 下挑唯一文件名，与 host 端命名一致：同名加 (1)(2) 后缀避免覆盖。
     * 返回绝对路径。
     */
    private fun resolveDestFilePath(rawName: String): String? {
        val dir = File(nanoTempCacheMergedDir())
        if (!dir.exists() && !dir.mkdirs()) return null
        val uniqueName = pickUniqueName(dir, rawName)
        return File(dir, uniqueName).absolutePath
    }

    private fun pickUniqueName(dir: File, rawName: String): String {
        val existing = (dir.listFiles()?.map { it.name } ?: emptyList()).toMutableSet()
        if (!existing.contains(rawName)) return rawName
        val dot = rawName.lastIndexOf('.')
        val stem = if (dot > 0) rawName.substring(0, dot) else rawName
        val ext = if (dot > 0) rawName.substring(dot) else ""
        var i = 1
        while (true) {
            val candidate = "$stem($i)$ext"
            if (!existing.contains(candidate)) return candidate
            i++
        }
    }

    private fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }
}

data class ConnectToHostReceiveUiState(
    val files: List<RemoteFileBean> = emptyList(),
    val refreshing: Boolean = false,
    val downloadTasks: List<DownloadTask> = emptyList(),
    val error: String? = null,
)
