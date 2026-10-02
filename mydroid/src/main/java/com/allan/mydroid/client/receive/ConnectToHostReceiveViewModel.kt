package com.allan.mydroid.client.receive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allan.mydroid.BuildConfig
import com.allan.mydroid.R
import com.allan.mydroid.client.HostEndpoint
import com.allan.mydroid.client.api.ClientApi
import com.allan.mydroid.client.api.ClientWsClient
import com.allan.mydroid.client.api.WsConnectionState
import com.allan.mydroid.client.beans.RemoteFileBean
import com.allan.mydroid.client.download.DownloadState
import com.allan.mydroid.client.download.DownloadTask
import com.allan.mydroid.client.download.GlobalDownloadObj
import com.allan.mydroid.repository.GlobalFileListRepoObj
import com.au.module_android.Globals
import com.au.module_android.log.logEx
import com.au.module_android.utils.withIOThread
import com.au.module_android.utilsmedia.openWith
import com.au.module_android.utilsmedia.saveFileToPublicDirectory
import com.au.module_android.utilsmedia.shareFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** 接收页面维护远端列表及连接；本地下载记录独立保留，传输由前台服务持有。 */
class ConnectToHostReceiveViewModel(private val endpoint: HostEndpoint?) : ViewModel(), KoinComponent {
    private val globalDownloadObj: GlobalDownloadObj by inject()
    private val fileListRepo: GlobalFileListRepoObj by inject()
    private val wsClient: ClientWsClient by inject()
    private val _uiState = MutableStateFlow(ConnectToHostReceiveUiState())
    val uiState = _uiState.asStateFlow()
    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            val tasksFlow = if (endpoint == null) globalDownloadObj.tasksFlow
                else globalDownloadObj.tasksFlow(endpoint.ip, endpoint.httpPort)
            tasksFlow.collect { tasks ->
                _uiState.update { it.copy(downloadTasks = tasks) }
            }
        }
        if (endpoint != null) {
            viewModelScope.launch {
                wsClient.connectionStateFlow.collect { state ->
                    _uiState.update { it.copy(connectionState = state) }
                    if (state == WsConnectionState.Connected) refreshList()
                    else refreshJob?.cancel()
                }
            }
            wsClient.connect(endpoint)
        }
    }

    fun refreshLocalFiles() {
        viewModelScope.launch {
            val missing = withIOThread {
                _uiState.value.downloadTasks.filter { it.state == DownloadState.Completed && !it.destFile.isFile }
            }
            missing.forEach { task -> globalDownloadObj.updateTask(task) { it.copy(state = DownloadState.Missing) } }
        }
    }

    fun reconnect() { if (endpoint != null) wsClient.reconnect() }

    fun refreshList() {
        val host = endpoint ?: return
        if (_uiState.value.connectionState != WsConnectionState.Connected || refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _uiState.update { it.copy(refreshing = true, error = null) }
            try {
                val files = ClientApi.requestFileList("http://${host.ip}:${host.httpPort}")
                _uiState.update { it.copy(files = files.filter { file -> file.uriUuid.isNotBlank() }.distinctBy { file -> file.uriUuid }) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: Globals.getString(R.string.something_error)) }
            } finally {
                _uiState.update { it.copy(refreshing = false) }
            }
        }
    }

    fun downloadFile(bean: RemoteFileBean) {
        val host = endpoint ?: return
        if (_uiState.value.connectionState != WsConnectionState.Connected) return
        viewModelScope.launch {
            globalDownloadObj.enqueue(DownloadTask(
                ip = host.ip, httpPort = host.httpPort, uriUuid = bean.uriUuid,
                name = bean.name ?: bean.uriUuid, fileSizeStr = bean.fileSizeStr,
                fileSize = bean.fileSize ?: 0L, mimeType = bean.mimeType ?: "application/octet-stream",
                url = ClientApi.downloadFileUrl(host.ip, host.httpPort, bean.uriUuid)
            ))
        }
    }

    fun cancelDownload(task: DownloadTask) = globalDownloadObj.cancel(task)

    fun retryDownload(task: DownloadTask) {
        if (_uiState.value.connectionState != WsConnectionState.Connected) return
        viewModelScope.launch { globalDownloadObj.retry(task) }
    }

    fun openDownloadedFile(task: DownloadTask) = localFileAction(task) {
        openWith(Globals.app, task.destFile, BuildConfig.APPLICATION_ID, Globals.getString(R.string.open))
    }

    fun shareDownloadedFile(task: DownloadTask) = localFileAction(task) {
        shareFile(Globals.app, task.destFile, Globals.getString(R.string.share))
    }

    fun saveDownloadedFile(task: DownloadTask) = localFileAction(task) {
        val uri = withIOThread { saveFileToPublicDirectory(Globals.app, task.destFile, false, "MyDroidTransfer") }
        checkNotNull(uri) { Globals.getString(R.string.save_failed) }
        _uiState.update { it.copy(error = Globals.getString(R.string.save_to_success).format("MyDroidTransfer")) }
    }

    fun deleteDownloadedFile(task: DownloadTask) = localFileAction(task) {
        withIOThread {
            check(task.destFile.delete()) { Globals.getString(R.string.something_error) }
            fileListRepo.reloadFileList()
        }
        globalDownloadObj.updateTask(task) { it.copy(state = DownloadState.Missing) }
    }

    fun removeRecord(task: DownloadTask) = globalDownloadObj.removeRecord(task)

    private fun localFileAction(task: DownloadTask, action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                if (!withIOThread { task.destFile.isFile }) {
                    globalDownloadObj.updateTask(task) { it.copy(state = DownloadState.Missing) }
                    error(Globals.getString(R.string.file_not_exist))
                }
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logEx(throwable = e) { "Local file action failed" }
                _uiState.update { it.copy(error = e.message ?: Globals.getString(R.string.something_error)) }
            }
        }
    }

    fun consumeError() { _uiState.update { it.copy(error = null) } }
    override fun onCleared() {
        if (endpoint != null) wsClient.close()
        super.onCleared()
    }
}

data class ConnectToHostReceiveUiState(
    val files: List<RemoteFileBean> = emptyList(),
    val refreshing: Boolean = false,
    val downloadTasks: List<DownloadTask> = emptyList(),
    val connectionState: WsConnectionState = WsConnectionState.Disconnected,
    val error: String? = null
)
