package com.allan.mydroid.client.receive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allan.mydroid.R
import com.allan.mydroid.client.HostEndpoint
import com.allan.mydroid.client.api.ClientApi
import com.allan.mydroid.client.api.ClientWsClient
import com.allan.mydroid.client.api.WsConnectionState
import com.allan.mydroid.client.beans.RemoteFileBean
import com.allan.mydroid.client.download.DownloadTask
import com.allan.mydroid.client.download.GlobalDownloadObj
import com.au.module_android.Globals
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** 接收页面维护远端列表、连接及当前下载状态，传输由前台服务持有。 */
class ConnectToHostReceiveViewModel(private val endpoint: HostEndpoint) : ViewModel(), KoinComponent {
    private val globalDownloadObj: GlobalDownloadObj by inject()
    private val wsClient: ClientWsClient by inject()
    private val _uiState = MutableStateFlow(ConnectToHostReceiveUiState())
    val uiState = _uiState.asStateFlow()
    private var refreshJob: Job? = null

    init {
        globalDownloadObj.clearFinishedTasks(endpoint.ip, endpoint.httpPort)
        viewModelScope.launch {
            globalDownloadObj.tasksFlow(endpoint.ip, endpoint.httpPort).collect { tasks ->
                _uiState.update { it.copy(downloadTasks = tasks) }
            }
        }
        viewModelScope.launch {
            wsClient.connectionStateFlow.collect { state ->
                _uiState.update { it.copy(connectionState = state) }
                if (state == WsConnectionState.Connected) refreshList()
                else refreshJob?.cancel()
            }
        }
        wsClient.connect(endpoint)
    }

    fun reconnect() = wsClient.reconnect()

    fun refreshList() {
        if (_uiState.value.connectionState != WsConnectionState.Connected || refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _uiState.update { it.copy(refreshing = true, error = null) }
            try {
                val files = ClientApi.requestFileList(ClientApi.baseUrl(endpoint.ip, endpoint.httpPort))
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
        if (_uiState.value.connectionState != WsConnectionState.Connected) return
        globalDownloadObj.enqueue(DownloadTask(
            ip = endpoint.ip, httpPort = endpoint.httpPort, uriUuid = bean.uriUuid,
            name = bean.name ?: bean.uriUuid, fileSize = bean.fileSize ?: 0L,
            url = ClientApi.downloadFileUrl(endpoint.ip, endpoint.httpPort, bean.uriUuid)
        ))
    }

    fun cancelDownload(task: DownloadTask) = globalDownloadObj.cancel(task)

    fun consumeError() { _uiState.update { it.copy(error = null) } }
    override fun onCleared() {
        wsClient.close()
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
