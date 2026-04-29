package com.allan.mydroid.views.receiver.nativefiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allan.mydroid.R
import com.allan.mydroid.api.Api
import com.allan.mydroid.api.WSApisConst.Companion.API_WS_CLIENT_INIT_CALLBACK
import com.allan.mydroid.api.WSApisConst.Companion.API_WS_INIT
import com.allan.mydroid.api.WSApisConst.Companion.API_WS_PING
import com.allan.mydroid.beansinner.ShareInHtml
import com.allan.mydroid.globals.MyDroidConst
import com.allan.mydroid.globals.ShareInUrisObj
import com.allan.mydroid.globals.nanoTempCacheMergedDir
import com.au.module_android.Globals
import com.au.module_android.log.logdNoFile
import com.au.module_android.utils.launchOnThread
import com.au.module_gson.toGsonString
import com.au.module_okhttp.OkhttpGlobal
import com.au.module_okhttp.creator.downloadFile
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class ReceiveFromPhoneNativeUiState(
    val ip: String = "",
    val port: String = "",
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val remoteClientName: String = "",
    val remoteClientColor: String = "",
    val files: List<ShareInHtml> = emptyList(),
    val downloads: Map<String, RemoteDownloadState> = emptyMap(),
    val error: String? = null,
)

data class RemoteDownloadState(
    val progress: Float = 0f,
    val isDownloading: Boolean = false,
    val error: String? = null,
)

class ReceiveFromPhoneNativeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ReceiveFromPhoneNativeUiState())
    val uiState: StateFlow<ReceiveFromPhoneNativeUiState> = _uiState.asStateFlow()

    private var remoteHttpPort: Int? = null
    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null

    fun updateIp(ip: String) {
        _uiState.update { it.copy(ip = ip, error = null) }
    }

    fun updatePort(port: String) {
        _uiState.update { it.copy(port = port, error = null) }
    }

    fun connect() {
        val ip = uiState.value.ip.trim()
        val httpPort = uiState.value.port.trim().toIntOrNull()
        if (ip.isEmpty() || httpPort == null) {
            _uiState.update { it.copy(error = Globals.getString(R.string.native_receive_invalid_ip_port)) }
            return
        }

        _uiState.update {
            it.copy(isConnecting = true, isConnected = false, error = null, files = emptyList())
        }

        viewModelScope.launchOnThread {
            try {
                val ipPort = Api.readWebsocketIpPort(ip, httpPort)
                    ?: throw IllegalStateException(Globals.getString(R.string.native_receive_read_port_failed))
                remoteHttpPort = ipPort.httpPort
                connectWebSocket(ip, ipPort.port)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isConnecting = false, isConnected = false, error = e.message)
                }
            }
        }
    }

    fun refreshFiles() {
        val ip = uiState.value.ip.trim()
        val httpPort = remoteHttpPort ?: uiState.value.port.trim().toIntOrNull() ?: return
        viewModelScope.launchOnThread {
            loadRemoteFiles(ip, httpPort)
        }
    }

    fun downloadFile(file: ShareInHtml) {
        val ip = uiState.value.ip.trim()
        val httpPort = remoteHttpPort ?: uiState.value.port.trim().toIntOrNull() ?: return
        val fileName = safeFileName(file.name ?: "file")
        val url = Api.buildFileDownloadUrl(ip, httpPort, file.uriUuid)
        updateDownload(file.uriUuid, RemoteDownloadState(isDownloading = true))

        viewModelScope.launchOnThread {
            try {
                val result = OkhttpGlobal.okHttpClient(2).downloadFile(
                    url = url,
                    dirPath = nanoTempCacheMergedDir(),
                    fileName = fileName,
                    useTempFile = true,
                    progressListener = { _, _, progress ->
                        updateDownload(file.uriUuid, RemoteDownloadState(progress, true))
                    }
                ) ?: throw IllegalStateException(Globals.getString(R.string.native_receive_download_failed))

                ShareInUrisObj.reloadFileList()
                MyDroidConst.onFileMergedData.setValueSafe(result)
                updateDownload(file.uriUuid, RemoteDownloadState(progress = 1f, isDownloading = false))
            } catch (e: Exception) {
                updateDownload(file.uriUuid, RemoteDownloadState(isDownloading = false, error = e.message))
            }
        }
    }

    private fun connectWebSocket(ip: String, wsPort: Int) {
        webSocket?.close(1000, "reconnect")
        webSocket = Api.connectWSServer(ip, wsPort, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val wsName = UUID.randomUUID().toString().replace("-", "").take(6)
                val data = mapOf(
                    "api" to API_WS_INIT,
                    "wsName" to wsName,
                    "platform" to "android/native",
                )
                webSocket.send(data.toGsonString())
                startHeartbeat(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parseWsMessage(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                heartbeatJob?.cancel()
                _uiState.update { it.copy(isConnected = false, isConnecting = false) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                heartbeatJob?.cancel()
                _uiState.update {
                    it.copy(isConnected = false, isConnecting = false, error = t.message)
                }
            }
        }, "/ws-test")
    }

    private fun parseWsMessage(text: String) {
        try {
            val json = JSONObject(text)
            if (json.optString("api") == API_WS_CLIENT_INIT_CALLBACK) {
                val data = json.optJSONObject("data")
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        isConnected = true,
                        remoteClientName = data?.optString("clientName").orEmpty(),
                        remoteClientColor = data?.optString("color").orEmpty(),
                        error = null,
                    )
                }
                refreshFiles()
            }
        } catch (e: Exception) {
            logdNoFile { "parse websocket message error ${e.message}" }
        }
    }

    private suspend fun loadRemoteFiles(ip: String, httpPort: Int) {
        try {
            val files = Api.requestRemoteFileList(ip, httpPort)
            _uiState.update { it.copy(files = files, error = null) }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = e.message) }
        }
    }

    private fun startHeartbeat(webSocket: WebSocket) {
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launchOnThread {
            while (true) {
                delay(12 * 1000L)
                webSocket.send(mapOf("api" to API_WS_PING).toGsonString())
            }
        }
    }

    private fun updateDownload(uriUuid: String, state: RemoteDownloadState) {
        _uiState.update {
            it.copy(downloads = it.downloads + (uriUuid to state))
        }
    }

    private fun safeFileName(name: String): String {
        val fileName = File(name).name
        return fileName.ifEmpty { "file" }
    }

    override fun onCleared() {
        heartbeatJob?.cancel()
        webSocket?.close(1000, "page close")
        webSocket = null
        super.onCleared()
    }
}
