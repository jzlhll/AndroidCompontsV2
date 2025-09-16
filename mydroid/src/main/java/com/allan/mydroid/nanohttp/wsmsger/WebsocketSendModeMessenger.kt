package com.allan.mydroid.nanohttp.wsmsger

import android.net.Uri
import androidx.lifecycle.Observer
import com.allan.mydroid.R
import com.allan.mydroid.api.WSApisConst.Companion.API_WS_FILE_DOWNLOAD_CANCEL
import com.allan.mydroid.api.WSApisConst.Companion.API_WS_FILE_DOWNLOAD_COMPLETE
import com.allan.mydroid.api.WSApisConst.Companion.API_WS_REQUEST_FILE
import com.allan.mydroid.api.WSApisConst.Companion.API_WS_SEND_FILE_CHUNK
import com.allan.mydroid.api.WSApisConst.Companion.API_WS_SEND_FILE_LIST
import com.allan.mydroid.api.WSApisConst.Companion.API_WS_SEND_FILE_NOT_EXIST
import com.allan.mydroid.api.WSApisConst.Companion.API_WS_SEND_NO_FILE_SIZE
import com.allan.mydroid.api.WSApisConst.Companion.API_WS_SEND_SMALL_FILE_CHUNK
import com.allan.mydroid.beans.WSResultBox
import com.allan.mydroid.beans.wsdata.FileListForHtmlData
import com.allan.mydroid.beans.wsdata.UriUuidData
import com.allan.mydroid.beans.wsdata.UriUuidSuccessData
import com.allan.mydroid.beans.wsdata.WSChunkActionData
import com.allan.mydroid.beansinner.UriRealInfoEx
import com.allan.mydroid.beansinner.UriRealInfoHtml
import com.allan.mydroid.globals.CODE_SUC
import com.allan.mydroid.globals.MyDroidConst
import com.allan.mydroid.globals.SMALL_FILE_DEFINE_SIZE
import com.allan.mydroid.globals.getWSSendFileChunkSize
import com.allan.mydroid.nanohttp.AbsWebSocketClientMessenger
import com.allan.mydroid.nanohttp.BufferManager
import com.allan.mydroid.nanohttp.WebsocketClientInServer
import com.au.module_android.Globals
import com.au.module_android.Globals.resStr
import com.au.module_android.json.fromJson
import com.au.module_android.json.toJsonString
import com.au.module_android.utils.launchOnIOThread
import com.au.module_android.utils.launchOnThread
import com.au.module_android.utils.launchOnUi
import com.au.module_android.utils.logdNoFile
import com.au.module_android.utils.logt
import com.au.module_androidui.toast.ToastBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.FileInputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set

class WebsocketSendModeMessenger(client: WebsocketClientInServer) : AbsWebSocketClientMessenger(client) {
    companion object {
        private const val DEBUG_SEND = true
        private const val DEBUG_SEND_DELAY_TS = 200L
        private val sUuidDowningFlagMap = ConcurrentHashMap<String, Unit>()
        private fun isDowning(uriUuid:String) : Boolean {
            return sUuidDowningFlagMap.containsKey(uriUuid)
        }
        private fun updateDowningTrue(uriUuid: String) {
            sUuidDowningFlagMap[uriUuid] = Unit
        }
        private fun updateDowningFalse(uriUuid: String) {
            sUuidDowningFlagMap.remove(uriUuid)
        }
    }

    private val sendUriMapOb = object : Observer<HashMap<String, UriRealInfoEx>> {
        override fun onChanged(value: HashMap<String, UriRealInfoEx>) {
            val cvtList = mutableListOf<UriRealInfoHtml>()
            value.values.forEach { urlRealInfoEx->
                if (urlRealInfoEx.isChecked) {
                    cvtList.add(urlRealInfoEx.copyToHtml())
                }
            }
            client.server.scope.launchOnThread {
                val msg = R.string.send_files_to_html.resStr()
                val data = FileListForHtmlData(cvtList)
                val ret = WSResultBox(CODE_SUC, msg, API_WS_SEND_FILE_LIST, data)
                val json = ret.toJsonString()
                logt { "${Thread.currentThread()} on map changed. send file list to html" }
                logt { "send:$json" }
                client.send(json)
            }
        }
    }

    override fun onOpen() {
        Globals.mainScope.launch {
            MyDroidConst.sendUriMap.observeForever(sendUriMapOb) //监听没问题
        }
    }

    override fun onClose() {
        Globals.mainScope.launch {
            MyDroidConst.sendUriMap.removeObserver(sendUriMapOb)
        }
    }

    override fun onMessage(origJsonStr:String, api:String, json: JSONObject) {
        val wsApiUuid = origJsonStr.fromJson<WSResultBox<UriUuidSuccessData>>()
        val uriUuid = wsApiUuid?.data?.uriUuid ?: ""
        val info = MyDroidConst.sendUriMap.value?.get(uriUuid)
        when (api) {
            API_WS_REQUEST_FILE -> {
                onSendFile(uriUuid, info)
            }
            API_WS_FILE_DOWNLOAD_CANCEL -> {
                updateDowningFalse(uriUuid)
            }

            API_WS_FILE_DOWNLOAD_COMPLETE -> {
                Globals.mainScope.launchOnUi {
                    info?.goodName()?.let { goodName->
                        val fmt = R.string.send_success_fmt.resStr()
                        ToastBuilder().setOnTop().setMessage(String.format(fmt, goodName)).toast()
                    }
                }
            }
        }
    }

    private fun readFromRealPath(info: UriRealInfoEx) : InputStream? {
        try {
            val path = info.goodPath()
            if (path != null) {
                return FileInputStream(path)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    private fun readFromContentResolver(uri: Uri) : InputStream?{
        try {
            //直接读取会跑出Security异常。提示app没有对于media的访问权限
            return Globals.app.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            // 可根据实际需要记录日志或上报错误
            e.printStackTrace()
        }
        return null
    }

//    fun onFileSendComplete(info: UriRealInfoEx?) {
//        logdNoFile { "${client.clientName} onFile Send Complete : $info" }
//        client.scope?.launchOnIOThread {
//        }
//    }

    private fun onSendFile(uriUuid:String, info: UriRealInfoEx?) {
        logdNoFile { "${client.clientName} onSend File : $info" }

        client.scope?.launchOnIOThread {
            var inputStream: InputStream? = null

            if (info != null) {
                val uri = info.uri

                //1. 尝试从真实路径直接读取
                logt { "onSend File try read from real Path ${info.goodPath()}" }
                inputStream = readFromRealPath(info)

                //2. 尝试从ContentResolver中直接读取
                if (inputStream == null) {
                    inputStream = readFromContentResolver(uri)
                    logt { "onSend File 222 $inputStream" }
                }
                //3. 转成media uri再读一次试试
//                if (inputStream == null) {
//                    val resolvedUri: Uri? = MediaUriResolver().resolveMediaStoreUri(Globals.app, uri)
//                    logt { "onSend File MediaStoreUri 333 $resolvedUri" }
//                    if (resolvedUri != null) {
//                        inputStream = readFromContentResolver(resolvedUri)
//                        logt { "onSend File MediaStoreUri 333 $inputStream" }
//                    }
//                }
            }

            val fileSize = info?.fileSize
            if (inputStream == null || fileSize == null) {
                if (fileSize == null || fileSize <= 0) {
                    client.send(
                        WSResultBox(
                            CODE_SUC,
                            R.string.no_file_size.resStr(),
                            API_WS_SEND_NO_FILE_SIZE,
                            UriUuidData(uriUuid)
                        ).toJsonString())
                } else {
                    client.send(
                        WSResultBox(
                            CODE_SUC,
                            R.string.file_not_exist.resStr(),
                            API_WS_SEND_FILE_NOT_EXIST,
                            UriUuidData(uriUuid)
                        ).toJsonString())
                }
            } else {
                sendFile(info.uriUuid, info.fileSize, info.goodName(), inputStream)
            }
        }
    }

    private suspend fun sendFile(uriUuid:String, fileSize:Long, fileName:String?, inputStream: InputStream) {
        updateDowningTrue(uriUuid)

        val fixFileName = fileName ?: ""
        val chunkSize = getWSSendFileChunkSize(fileSize).toInt()
        val buffer = ByteArray(chunkSize)

        val mgr = BufferManager(uriUuid, chunkSize)

        // 分片发送文件内容
        var bytesRead: Int
        var index = 0
        var offset = 0L

        val api = if(fileSize >= SMALL_FILE_DEFINE_SIZE) API_WS_SEND_FILE_CHUNK else API_WS_SEND_SMALL_FILE_CHUNK
        val isNotFull = fileSize % chunkSize > 0
        val totalChunks = (fileSize / chunkSize).toInt() + (if(isNotFull) 1 else 0) //肯定大于0

        val startJson = createStartJson(api, uriUuid, fileSize, totalChunks, fixFileName)
        logt { "send file start $startJson" }
        client.send(startJson)
        delay(100)

        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            val chunk = if (bytesRead == buffer.size) buffer else buffer.copyOf(bytesRead)
            val arr = mgr.buildChunkPacket(index++, totalChunks, offset, bytesRead, chunk)
            offset += bytesRead
            client.send(arr)
            if (DEBUG_SEND) {
                delay(DEBUG_SEND_DELAY_TS)
            }
            if (!isDowning(uriUuid)) {
                break
            }
        }

        val isDowning = isDowning(uriUuid)
        updateDowningFalse(uriUuid)
        if (!isDowning) {
            val cancelJson = createCancelJson(api, uriUuid, fileSize, totalChunks, fixFileName)
            client.send(cancelJson)
            return
        }
    }

    private fun createStartJson(api: String, uriUuid: String, fileSize: Long, totalChunks: Int, fixFileName: String): String {
        return WSResultBox(
            CODE_SUC,
            R.string.send_file_start.resStr(),
            api,
            WSChunkActionData(
                "start",
                uriUuid,
                fileSize,
                totalChunks,
                fixFileName
            )
        ).toJsonString()
    }

    private fun createCancelJson(api: String, uriUuid: String, fileSize: Long, totalChunks: Int, fixFileName: String): String {
        return WSResultBox(
            CODE_SUC,
            R.string.download_canceled.resStr(),
            api,
            WSChunkActionData(
                "cancel",
                uriUuid,
                fileSize,
                totalChunks,
                fixFileName
            )
        ).toJsonString()
    }
}