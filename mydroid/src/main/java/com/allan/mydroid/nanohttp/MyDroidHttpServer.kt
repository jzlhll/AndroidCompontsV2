package com.allan.mydroid.nanohttp

import com.allan.mydroid.R
import com.allan.mydroid.api.ABORT_UPLOAD_CHUNKS
import com.allan.mydroid.api.MERGE_CHUNKS
import com.allan.mydroid.api.MyDroidMode
import com.allan.mydroid.api.READ_WEBSOCKET_IP_PORT
import com.allan.mydroid.api.REQUEST_FILE_LIST
import com.allan.mydroid.api.TEXT_CHAT_READ_WEBSOCKET_IP_PORT
import com.allan.mydroid.api.UPLOAD_CHUNK
import com.allan.mydroid.beans.httpdata.IpPortResult
import com.allan.mydroid.globals.CODE_SUC
import com.allan.mydroid.globals.MyDroidConst
import com.allan.mydroid.globals.MyDroidConstServer
import com.allan.mydroid.globals.ShareInUrisObj
import com.allan.mydroid.globals.okJsonResponse
import com.au.module_android.Globals
import com.au.module_android.Globals.resStr
import com.au.module_android.api.ResultBean
import com.au.module_android.json.toJsonString
import com.au.module_android.utils.logdNoFile
import com.modulenative.AppNative
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.Response.Status
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException
import java.io.IOException
import java.net.URLEncoder

interface IChunkMgr {
    fun handleUploadChunk(session: NanoHTTPD.IHTTPSession): Response
    fun handleMergeChunk(session: NanoHTTPD.IHTTPSession) : Response
    fun handleAbortChunk(session: NanoHTTPD.IHTTPSession) : Response
}

interface IMyDroidHttpServer {
    /**
     * 启动一些周期性活动。
     */
    fun startPeriodWork()
}

class MyDroidHttpServer(httpPort: Int) : NanoHTTPD(httpPort), IMyDroidHttpServer {
    init {
        tempFileManagerFactory = MyDroidTempFileMgrFactory()
    }

    private val chunksMgr: IChunkMgr = MyDroidHttpChunksMgr()

    override fun serve(session: IHTTPSession): Response {
        // 处理跨域预检请求 (OPTIONS)
        if (session.method == Method.OPTIONS) {
            return handleOptionRequest()
        }

        val ct = ContentType(session.headers["content-type"]).tryUTF8()
        session.headers.put("content-type", ct.contentTypeHeader)

        return when (session.method) {
            Method.GET -> handleGetRequest(session)
            Method.POST -> handlePostRequest(session)
            else -> newFixedLengthResponse(Status.NOT_FOUND, MIME_PLAINTEXT, "404")
        }
    }

    /*
     response.addHeader("Access-Control-Allow-Headers", "Content-Type, Accept, token, Authorization, " +
         "X-Auth-Token,X-XSRF-TOKEN,Access-Control-Allow-Headers");
 response.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, HEAD");
 response.addHeader("Access-Control-Allow-Credentials", "true");
 response.addHeader("Access-Control-Allow-Origin", "*");
 response.addHeader("Access-Control-Max-Age", "" + 42 * 60 * 60);
     */

    private fun handleOptionRequest(): Response {
        val response = newFixedLengthResponse(Status.OK, MIME_PLAINTEXT, "")
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST")
        response.addHeader("Access-Control-Allow-Headers", "Content-Type")
        return response
    }

    private fun handleGetRequest(session: IHTTPSession): Response {
        val url = session.uri ?: ""
        logdNoFile { "handle Get Request $url" }
        MyDroidConstServer.updateAliveTs("http get request")
        val error:String
        when {
            // 主页面请求
            url == "/" -> {
                when (MyDroidConst.currentDroidMode) {
                    MyDroidMode.Send -> {
                        return serveAssetFile("transfer/ReceiveFromPhone.html")
                    }
                    MyDroidMode.Receiver -> {
                        return serveAssetFile("transfer/SendToPhone.html")
                    }
                    MyDroidMode.Middle -> {
                        return serveAssetFile("transfer/MiddleServer.html")
                    }
                    else -> {
                        error = Globals.getString(R.string.server_not_support) + "(E02)"
                    }
                }
            }
            url == READ_WEBSOCKET_IP_PORT ->
                return getWebsocketIpPort()
            url == TEXT_CHAT_READ_WEBSOCKET_IP_PORT -> {
                return getWebsocketIpPortWrap()
            }
            url.startsWith("/file_download_uuid/") -> {
                return fileDownload(url.substring("/file_download_uuid/".length))
            }
            // JS / html 文件请求
            url.endsWith(".js") || url.endsWith(".html") -> {
                val jsName = url.substring(1)
                return serverAssetTextFile("transfer/$jsName")
            }
            else -> {
                error = Globals.getString(R.string.server_not_support) + "(E01)"
            }
        }

        return newFixedLengthResponse(Status.NOT_FOUND, MIME_PLAINTEXT, error)
    }


    fun fileDownload(uriUuid:String) : NanoHTTPD.Response {
        try {
            val info = ShareInUrisObj.shareInAndReceiveBeans?.find { it.uriUuid == uriUuid }  ?: return fileNotFoundResponse()
            val fileSize = info.fileSize ?: 0
            if (fileSize <= 0) {
                return fileSizeIs0Response()
            }
            val uri = info.uri
            val filename = info.name ?: "file"
            logdNoFile { "file Download1 $uri size:$fileSize" }

            if (!ShareInUrisObj.isHostThisUri(uri)) {
                logdNoFile { "file Download this uri is donot has permission." }
                return newFixedLengthResponse(Status.INTERNAL_ERROR, MIME_PLAINTEXT, "No permission yet todo translate.")
            }
            //Globals.app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            val inputStream = Globals.app.contentResolver.openInputStream(uri)
            logdNoFile { "file Download2 $uri ${inputStream?.available()}" }
            // 1. 创建响应，指定状态码为 OK，MIME 类型为二进制流（强制下载）
            val response = newFixedLengthResponse(Status.OK,
                "application/octet-stream", inputStream, fileSize)
            logdNoFile { "file response1111" }
            // 2. 设置 Content-Disposition 头，这是触发浏览器下载的关键
            // 使用 "attachment" 表示希望浏览器将响应体保存为文件
            // filename 指定建议的文件名，浏览器可能会使用它作为默认保存名
            val encodedFileName = URLEncoder.encode(filename, "UTF-8")
                .replace("\\+".toRegex(), "%20") // 替换空格编码
            response.addHeader(
                "Content-Disposition",
                "attachment; filename=\"" +
                        String(filename.toByteArray(charset("GBK")),
                            charset("ISO-8859-1")) + "\"; " +
                        "filename*=UTF-8''" + encodedFileName
            )

            // 3. （可选但推荐）设置 Content-Length 头
            response.addHeader("Content-Length", "" + fileSize)
            // 4. （可选）设置 Content-Type，如果你确切知道文件类型，可以设置更具体的 MIME 类型
            // 例如 "image/jpeg", "application/pdf"
            // 但对于强制下载，"application/octet-stream" 是通用选择
            return response
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            logdNoFile { "file Download error1" }
            return fileNotFoundResponse()
        } catch (e: IOException) {
            e.printStackTrace()
            logdNoFile { "file Download error2" }
            return newFixedLengthResponse(Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error reading file.")
        } catch (e: Exception) {
            e.printStackTrace()
            logdNoFile { "file Download error3" }
            return newFixedLengthResponse(Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error reading file 2.")
        }
    }

    private fun fileNotFoundResponse() : NanoHTTPD.Response {
        return newFixedLengthResponse(Status.NOT_FOUND, MIME_PLAINTEXT, "File not found.")
    }
    private fun fileSizeIs0Response() : NanoHTTPD.Response {
        return newFixedLengthResponse(Status.NOT_FOUND, MIME_PLAINTEXT, "File size is 0.")
    }

    private fun handlePostRequest(session: IHTTPSession): Response {
        MyDroidConstServer.updateAliveTs("http post request")
        return when (session.uri) {
            UPLOAD_CHUNK -> chunksMgr.handleUploadChunk(session)
            MERGE_CHUNKS -> chunksMgr.handleMergeChunk(session)
            ABORT_UPLOAD_CHUNKS -> chunksMgr.handleAbortChunk(session)
            READ_WEBSOCKET_IP_PORT -> getWebsocketIpPort()
            TEXT_CHAT_READ_WEBSOCKET_IP_PORT -> getWebsocketIpPortWrap()
            REQUEST_FILE_LIST -> getFileList()
            else -> newFixedLengthResponse(R.string.invalid_request_from_appserver.resStr()) // 或者其他默认响应
        }
    }

    private fun getFileList() : Response {
        return runBlocking {
            val beans= ShareInUrisObj.loadShareInAndReceiveBeans()
            val json = beans.toJsonString()
            if (json.isNotEmpty()) {
                ResultBean(CODE_SUC, "Success!", json).okJsonResponse()
            } else {
                newFixedLengthResponse(R.string.invalid_request_from_appserver.resStr()) // 或者其他默认响应
            }
        }
    }

    private fun getWebsocketIpPortWrap(): Response {
        if (MyDroidConst.currentDroidMode == MyDroidMode.TextChat) {
            return getWebsocketIpPort()
        } else {
            val error = Globals.getString(R.string.server_is_not_textchat)
            return newFixedLengthResponse(Status.NOT_FOUND, MIME_PLAINTEXT, error)
        }
    }

    private fun getWebsocketIpPort() : Response{
        val data = MyDroidConst.ipPortData
        val ip = data.value?.ip
        val wsPort = data.value?.webSocketPort
        val httpPort = data.value?.httpPort

        return if (ip != null && wsPort != null && httpPort != null) {
            val info = IpPortResult(ip, wsPort, httpPort)
            logdNoFile { "get websocket ipPort $info" }
            ResultBean(CODE_SUC, "Success!", info).okJsonResponse()
        } else {
            newFixedLengthResponse(R.string.invalid_request_from_appserver.resStr()) // 或者其他默认响应
        }
    }

    private fun serveAssetFile(assetFile: String, replacementBlock:((String)->String) = { it }) : Response {
        return try {
            val text = AppNative.asts(Globals.app, assetFile)
            val response = newFixedLengthResponse(replacementBlock(text))
            logdNoFile { "serve Asset File read success $assetFile." }
            return response
        } catch (_: FileNotFoundException) {
            newFixedLengthResponse(Status.INTERNAL_ERROR, "application/json", """"{"error": "File $assetFile not found"}""")
        }
    }

    private fun serverAssetTextFile(jsAssetFile:String) : Response{
        try {
            val text = AppNative.asts(Globals.app, jsAssetFile)
            val response = newFixedLengthResponse(text)
            logdNoFile { "serve Asset File read success $jsAssetFile." }
            return response
        } catch (_: IOException) {
            return newFixedLengthResponse(Status.NOT_FOUND, MIME_PLAINTEXT, "404 Not Found")
        }
    }

    override fun startPeriodWork() {
//        handle.removeCallbacks(mPeriodSpaceRun)
//        handle.post(mPeriodSpaceRun)
    }

    override fun stop() {
        logdNoFile { "stop all." }
        super.stop()
    }
}