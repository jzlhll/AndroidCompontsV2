package com.allan.mydroid.client.api

import com.allan.mydroid.api.GET_MODE
import com.allan.mydroid.api.MyDroidMode
import com.allan.mydroid.beans.httpdata.IpPortResult
import com.allan.mydroid.client.beans.RemoteFileBean
import com.allan.mydroid.nanohttp.CODE_SUC
import com.au.module_android.Globals
import com.au.module_android.log.logdNoFile
import com.au.module_android.utils.withIOThread
import com.au.module_gson.fromGson
import com.au.module_gson.fromGsonList
import com.au.module_okhttp.api.ResultBean
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlin.time.Duration.Companion.milliseconds

/**
 * client 端 HTTP API。独立于 host 端 Api 对象，每次调用显式传完整 baseUrl。
 * 不修改 Api.currentBaseUrl（host 自身 baseUrl），避免状态污染。
 *
 * - 所有方法 suspend + withIOThread，异常抛给 ViewModel 处理。
 * - HTTP 200 不代表业务成功，必须 body.code == "0"（host ResultBean.code 是 String）。
 * - 业务 code != "0" 时抛 [ClientApiException] 携带 msg。
 */
object ClientApi {
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    /** GET /get-mode 总超时 1.5s。 */
    private val MODE_FETCH_TIMEOUT = 1500.milliseconds

    class ClientApiException(val code: String, msg: String) : Exception(msg)

    fun baseUrl(ip: String, port: Int): String = HttpUrl.Builder()
        .scheme("http").host(ip).port(port).build().toString().removeSuffix("/")

    /** POST /read-websocket-ip-port，返回 host 的 ws/http 端口信息。 */
    suspend fun fetchWsIpPort(baseUrl: String): IpPortResult = withIOThread {
        val resp = postJson("$baseUrl/read-websocket-ip-port", "{}")
        val bean = resp.fromGson<ResultBean<IpPortResult>>() ?: throw IllegalStateException("parse IpPortResult failed: $resp")
        ensureSuc(bean.code, bean.msg)
        bean.data ?: throw IllegalStateException("IpPortResult data is null")
    }

    /**
     * GET /get-mode，返回 host 当前模式。
     * host 端返回 mode.name 字符串；client 用 [MyDroidMode.valueOf] 解析。
     * 总超时 1.5s（点击 host 后快速反馈，超时即视为信息过期触发重扫）。
     * 失败（超时 / 网络异常 / code != "0" / data 为 null / 未知 name）抛异常。
     */
    suspend fun fetchMode(baseUrl: String): MyDroidMode = withIOThread {
        val request = Request.Builder().url("$baseUrl$GET_MODE").get().build()
        val resp = withTimeoutOrNull(MODE_FETCH_TIMEOUT) {
            executeText(request)
        } ?: throw IllegalStateException("fetch mode timeout")
        val bean = resp.fromGson<ResultBean<String>>() ?: throw IllegalStateException("parse mode failed: $resp")
        ensureSuc(bean.code, bean.msg)
        val name = bean.data ?: throw IllegalStateException("mode data is null")
        runCatching { MyDroidMode.valueOf(name) }.getOrElse {
            throw IllegalStateException("unknown mode name: $name")
        }
    }

    /** POST /request-file-list，返回 host 可下载文件列表。 */
    suspend fun requestFileList(baseUrl: String): List<RemoteFileBean> = withIOThread {
        val resp = postJson("$baseUrl/request-file-list", "{}")
        val bean = resp.fromGson<ResultBean<String>>() ?: throw IllegalStateException("parse file list failed: $resp")
        ensureSuc(bean.code, bean.msg)
        val dataStr = bean.data ?: throw IllegalStateException("file list data is null")
        dataStr.fromGsonList<RemoteFileBean>()
    }

    /**
     * POST /upload-chunk multipart 上传一个分片。
     * host 端 NanoHTTPD 用 session.parseBody 解析，文件 part name 必须是 "chunk"。
     *
     * 分块由调用方单次顺序读取，摘要与请求体使用同一份字节。
     */
    suspend fun uploadChunk(
        baseUrl: String,
        chunk: ByteArray,
        fileName: String,
        chunkIndex: Int,
        totalChunks: Int,
        uploadId: String
    ): String = withIOThread {
        val url = "$baseUrl/upload-chunk"
        val multipartBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("fileName", fileName)
            .addFormDataPart("chunkIndex", chunkIndex.toString())
            .addFormDataPart("totalChunks", totalChunks.toString())
            .addFormDataPart("hashMode", "stream")
            .addFormDataPart("uploadId", uploadId)

        multipartBuilder.addFormDataPart("chunk", fileName, chunk.toRequestBody(null))

        val request = Request.Builder().url(url).post(multipartBuilder.build()).build()
        val resp = executeText(request)
        logdNoFile { "upload chunk resp: $resp" }
        val bean = resp.fromGson<ResultBean<Unit>>() ?: throw IllegalStateException("parse uploadChunk resp failed: $resp")
        ensureSuc(bean.code, bean.msg)
        resp
    }

    /** POST /merge-chunks JSON。 */
    suspend fun mergeChunks(
        baseUrl: String,
        md5: String,
        fileName: String,
        totalChunks: Int,
        lastModified: Long,
        uploadId: String = "",
        hashMode: String = ""
    ): String = withIOThread {
        val json = JSONObject().apply {
            put("hashMode", hashMode)
            put("md5", md5)
            put("fileName", fileName)
            put("totalChunks", totalChunks)
            put("lastModified", lastModified)
            put("uploadId", uploadId)
        }.toString()
        val resp = postJson("$baseUrl/merge-chunks", json)
        val bean = resp.fromGson<ResultBean<Map<String, String>>>() ?: throw IllegalStateException("parse mergeChunks resp failed: $resp")
        ensureSuc(bean.code, bean.msg)
        if (hashMode == "stream") {
            check(bean.data?.get("md5").equals(md5, ignoreCase = true)) { "Invalid merged MD5" }
        }
        bean.data?.get("fileName") ?: fileName
    }

    /** POST /abort-upload-chunks JSON。 */
    suspend fun abortUploadChunks(baseUrl: String, md5: String, fileName: String, uploadId: String = "", hashMode: String = ""): String = withIOThread {
        val json = JSONObject().apply {
            put("hashMode", hashMode)
            put("md5", md5)
            put("fileName", fileName)
            put("uploadId", uploadId)
        }.toString()
        val resp = postJson("$baseUrl/abort-upload-chunks", json)
        val bean = resp.fromGson<ResultBean<Unit>>() ?: throw IllegalStateException("parse abort resp failed: $resp")
        ensureSuc(bean.code, bean.msg)
        resp
    }

    /** 拼下载 URL 字符串。实际下载由 DownloadService 用 OkHttp 流式 GET 执行。 */
    fun downloadFileUrl(ip: String, httpPort: Int, uriUuid: String): String {
        return HttpUrl.Builder().scheme("http").host(ip).port(httpPort)
            .addPathSegment("file_download_uuid").addPathSegment(uriUuid).build().toString()
    }

    private suspend fun postJson(url: String, json: String): String = withIOThread {
        val request = Request.Builder()
            .url(url)
            .post(json.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        executeText(request)
    }

    private suspend fun executeText(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = ClientHttp.forUrl(request.url).newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val text = response.use {
                        if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
                        it.body?.string() ?: throw IOException("Empty response")
                    }
                    continuation.resume(text)
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            }
        })
    }

    private fun ensureSuc(code: String?, msg: String?) {
        if (code != CODE_SUC) {
            throw ClientApiException(code ?: "null", msg ?: "unknown error")
        }
    }
}
