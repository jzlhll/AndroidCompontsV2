package com.allan.mydroid.client.send

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allan.mydroid.R
import com.allan.mydroid.client.HostEndpoint
import com.allan.mydroid.client.api.ClientChunkUploader
import com.allan.mydroid.client.api.ClientWsClient
import com.allan.mydroid.client.api.WsFrame
import com.allan.mydroid.client.api.WsConnectionState
import com.allan.mydroid.client.beans.SelectedFile
import com.au.module_android.Globals
import com.au.module_android.log.loge
import com.au.module_android.utils.launchOnIOThread
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * client → host 发送模式（对应 host MyDroidMode.Receiver）。
 *
 * - 注入 [ClientChunkUploader] / [ClientWsClient]，endpoint 由构造参数传入。
 * - WS 仅用于接收 host 推送的 leftSpace 显示在顶部；上传走 HTTP。
 */
class ConnectToHostSendViewModel(
    private val endpoint: HostEndpoint
) : ViewModel(), KoinComponent {

    private val chunkUploader: ClientChunkUploader by inject()
    private val wsClient: ClientWsClient by inject()

    private val _uiState = MutableStateFlow(ConnectToHostSendUiState())
    val uiState: StateFlow<ConnectToHostSendUiState> = _uiState.asStateFlow()

    private val baseUrl: String
        get() = "http://${endpoint.ip}:${endpoint.httpPort}"

    private var uploadJob: Job? = null

    init {
        wsClient.connect(endpoint)
        observeLeftSpace()
    }

    private fun observeLeftSpace() {
        viewModelScope.launch {
            wsClient.incomingFrameFlow.collectLatest { frame ->
                if (frame is WsFrame.LeftSpace && frame.leftSpaceStr.isNotEmpty()) {
                    _uiState.update { it.copy(leftSpace = frame.leftSpaceStr) }
                }
            }
        }
        viewModelScope.launch {
            wsClient.connectionStateFlow.collectLatest { state ->
                _uiState.update { it.copy(connectionState = state) }
            }
        }
    }

    fun onFilesPicked(files: List<SelectedFile>) {
        if (files.isEmpty()) return
        _uiState.update {
            it.copy(selectedFiles = it.selectedFiles + files)
        }
    }

    fun removeSelectedFile(uri: android.net.Uri) {
        _uiState.update {
            it.copy(selectedFiles = it.selectedFiles.filterNot { f -> f.uri == uri })
        }
    }

    fun startUpload() {
        val files = _uiState.value.selectedFiles
        if (files.isEmpty() || _uiState.value.uploading) return
        uploadJob = viewModelScope.launchOnIOThread {
            _uiState.update { it.copy(uploading = true, progress = 0f, currentUploadingName = files.first().name, error = null) }
            val done = mutableListOf<String>()
            for (file in files) {
                try {
                    _uiState.update { it.copy(currentUploadingName = file.name, progress = 0f) }
                    chunkUploader.upload(
                        baseUrl = baseUrl,
                        uri = file.uri,
                        fileName = file.name,
                        totalSize = file.size
                    ) { sent, total ->
                        val p = if (total > 0) sent.toFloat() / total else 0f
                        _uiState.update { it.copy(progress = p) }
                    }
                    done.add(file.name)
                } catch (e: Exception) {
                    loge { "upload ${file.name} failed: ${e.message}" }
                    _uiState.update { it.copy(error = Globals.getString(R.string.something_error) + ": ${e.message}") }
                    break
                }
            }
            val uploadedUris = files.take(done.size).map { it.uri }.toSet()
            _uiState.update { st ->
                st.copy(
                    uploading = false,
                    progress = 1f,
                    currentUploadingName = null,
                    doneFiles = st.doneFiles + done,
                    selectedFiles = st.selectedFiles.filterNot { it.uri in uploadedUris }
                )
            }
        }
    }

    fun cancelUpload() {
        uploadJob?.cancel()
        uploadJob = null
        _uiState.update { it.copy(uploading = false, progress = 0f, currentUploadingName = null) }
    }

    fun consumeError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        wsClient.close()
    }
}

data class ConnectToHostSendUiState(
    val selectedFiles: List<SelectedFile> = emptyList(),
    val uploading: Boolean = false,
    val currentUploadingName: String? = null,
    val progress: Float = 0f,
    val leftSpace: String? = null,
    val doneFiles: List<String> = emptyList(),
    val error: String? = null,
    val connectionState: WsConnectionState = WsConnectionState.Disconnected
)
