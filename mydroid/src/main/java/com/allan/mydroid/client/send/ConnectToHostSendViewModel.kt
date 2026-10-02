package com.allan.mydroid.client.send

import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allan.mydroid.R
import com.allan.mydroid.repository.TransferFiles
import com.allan.mydroid.client.HostEndpoint
import com.allan.mydroid.client.api.ClientChunkUploader
import com.allan.mydroid.client.api.ClientWsClient
import com.allan.mydroid.client.api.WsFrame
import com.allan.mydroid.client.api.WsConnectionState
import com.allan.mydroid.client.beans.SelectedFile
import com.allan.mydroid.client.beans.UploadState
import com.au.module_android.Globals
import com.au.module_android.log.logEx
import com.au.module_android.utils.withIOThread
import com.au.module_android.utilsmedia.myParseSuspend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

/** 手机向主机串行发送文件，保留逐文件结果，取消清理结束后才允许重试。 */
class ConnectToHostSendViewModel(private val endpoint: HostEndpoint) : ViewModel(), KoinComponent {
    private val chunkUploader: ClientChunkUploader by inject()
    private val wsClient: ClientWsClient by inject()
    private val _uiState = MutableStateFlow(ConnectToHostSendUiState())
    val uiState = _uiState.asStateFlow()
    private val baseUrl get() = "http://${endpoint.ip}:${endpoint.httpPort}"
    private var uploadJob: Job? = null
    private var stopRequested = false

    init {
        viewModelScope.launch {
            wsClient.incomingFrameFlow.collect { frame ->
                if (frame is WsFrame.LeftSpace) _uiState.update { it.copy(leftSpace = frame.leftSpaceStr) }
            }
        }
        viewModelScope.launch {
            wsClient.connectionStateFlow.collect { state ->
                _uiState.update { it.copy(connectionState = state) }
                if (state != WsConnectionState.Connected && _uiState.value.uploading) {
                    stopRequested = true
                    if (state == WsConnectionState.ModeChanged) cancelUpload()
                }
            }
        }
        wsClient.connect(endpoint)
    }

    fun reconnect() = wsClient.reconnect()

    fun onFilesPicked(files: List<SelectedFile>) {
        if (files.isEmpty() || _uiState.value.busy) return
        val known = _uiState.value.selectedFiles.map { it.uri }.toSet()
        val additions = files.distinctBy { it.uri }.filter { it.uri !in known }
        _uiState.update { it.copy(selectedFiles = it.selectedFiles + additions, preparingSelection = true) }
        viewModelScope.launch {
            try {
                for (file in additions) {
                    try {
                        val info = withIOThread { file.uri.myParseSuspend() }
                        updateFile(file.id) { it.copy(name = info.name, size = info.fileLength) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        updateFile(file.id) { it.copy(state = UploadState.Failed, error = e.message) }
                    }
                }
            } finally {
                _uiState.update { it.copy(preparingSelection = false) }
            }
        }
    }

    fun removeSelectedFile(uri: Uri) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(selectedFiles = it.selectedFiles.filterNot { file -> file.uri == uri }) }
    }

    fun startUpload() {
        if (_uiState.value.busy || _uiState.value.connectionState != WsConnectionState.Connected) return
        val files = _uiState.value.selectedFiles.filter { it.state != UploadState.Completed }
        if (files.isEmpty()) return
        stopRequested = false
        _uiState.update { it.copy(uploading = true, stopping = false, error = null) }
        uploadJob = viewModelScope.launch {
            try {
                for (file in files) {
                    if (stopRequested || _uiState.value.connectionState != WsConnectionState.Connected) break
                    var cache: File? = null
                    try {
                        updateFile(file.id) { it.copy(state = UploadState.Preparing, progress = 0f, error = null, bytesPerSecond = 0) }
                        val info = withIOThread { file.uri.myParseSuspend() }
                        var uri = file.uri
                        var size = info.fileLength
                        if (size <= 0) {
                            // 大小未知的提供方先复制到本次尝试的独立缓存，以实际字节数为准。
                            val target = withIOThread { TransferFiles.temporaryFile() }
                            cache = target
                            size = withIOThread {
                                Globals.app.contentResolver.openInputStream(uri).use { input ->
                                    checkNotNull(input) { "Cannot open selected file" }
                                    target.outputStream().use { output ->
                                        val buffer = ByteArray(64 * 1024)
                                        while (true) {
                                            currentCoroutineContext().ensureActive()
                                            val count = input.read(buffer)
                                            if (count == -1) break
                                            output.write(buffer, 0, count)
                                        }
                                    }
                                }
                                target.length()
                            }
                            uri = Uri.fromFile(target)
                        }
                        require(size > 0) { Globals.getString(R.string.transfer_empty_file) }
                        var modified = info.lastModified?.takeIf { it > 0 } ?: System.currentTimeMillis()
                        // 解析器的 content 分支返回 MediaStore 秒值，File 分支返回毫秒值。
                        if (!info.isFile && modified in 1..9999999999L) modified *= 1000
                        updateFile(file.id) { it.copy(name = info.name, size = size) }
                        var startedAt = 0L
                        val savedName = chunkUploader.upload(baseUrl, uri, info.name, size, modified,
                            onMerging = { updateFile(file.id) { it.copy(state = UploadState.Merging) } },
                            onCleanupFailed = {
                                _uiState.update { it.copy(error = Globals.getString(R.string.transfer_cleanup_failed)) }
                            },
                            onProgress = { sent, total ->
                                val now = SystemClock.elapsedRealtime()
                                if (sent == 0L) startedAt = now
                                val elapsed = now - startedAt
                                val speed = if (elapsed > 0) (sent * 1000.0 / elapsed).toLong() else 0L
                                updateFile(file.id) { it.copy(state = UploadState.Uploading, progress = sent.toFloat() / total, bytesPerSecond = speed) }
                            })
                        updateFile(file.id) { it.copy(state = UploadState.Completed, progress = 1f, savedName = savedName) }
                    } catch (e: CancellationException) {
                        updateFile(file.id) { it.copy(state = UploadState.Canceled) }
                        throw e
                    } catch (e: Exception) {
                        logEx(throwable = e) { "Upload failed: ${file.name}" }
                        updateFile(file.id) { it.copy(state = UploadState.Failed, error = e.message) }
                    } finally {
                        cache?.delete()
                    }
                }
            } finally {
                _uiState.update { it.copy(uploading = false, stopping = false) }
                uploadJob = null
            }
        }
    }

    fun cancelUpload() {
        stopRequested = true
        _uiState.update { it.copy(stopping = true) }
        if (_uiState.value.selectedFiles.none { it.state == UploadState.Merging }) uploadJob?.cancel()
    }

    suspend fun stopAndAwait() {
        cancelUpload()
        uploadJob?.join()
    }

    fun consumeError() { _uiState.update { it.copy(error = null) } }

    private fun updateFile(id: String, update: (SelectedFile) -> SelectedFile) {
        _uiState.update { state -> state.copy(selectedFiles = state.selectedFiles.map { if (it.id == id) update(it) else it }) }
    }

    override fun onCleared() {
        wsClient.close()
        super.onCleared()
    }
}

data class ConnectToHostSendUiState(
    val selectedFiles: List<SelectedFile> = emptyList(),
    val uploading: Boolean = false,
    val preparingSelection: Boolean = false,
    val stopping: Boolean = false,
    val leftSpace: String? = null,
    val error: String? = null,
    val connectionState: WsConnectionState = WsConnectionState.Disconnected
) {
    val busy get() = uploading || preparingSelection
}
