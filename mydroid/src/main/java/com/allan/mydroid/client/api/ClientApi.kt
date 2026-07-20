package com.allan.mydroid.client.api

import android.net.Uri
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
import com.au.module_okhttp.OkhttpGlobal
import com.au.module_okhttp.api.ResultBean
import com.au.module_okhttp.creator.awaitHttpResultStr
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
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

    private class ClientApiException(val code: String, msg: String) : Exception(msg)

    private val httpClient get() = OkhttpGlobal.okHttpClient()

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
        val resp = try {
            withTimeout(MODE_FETCH_TIMEOUT) {
                request.awaitHttpResultStr(httpClient) ?: "{}"
            }
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException("fetch mode timeout", e)
        }
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
     * @param offset uri 输入流的起始偏移
     * @param length 本次分片字节数
     */
    suspend fun uploadChunk(
        baseUrl: String,
        uri: Uri,
        fileName: String,
        chunkIndex: Int,
        totalChunks: Int,
        md5: String,
        offset: Long,
        length: Long
    ): String = withIOThread {
        val url = "$baseUrl/upload-chunk"
        val multipartBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("fileName", fileName)
            .addFormDataPart("chunkIndex", chunkIndex.toString())
            .addFormDataPart("totalChunks", totalChunks.toString())
            .addFormDataPart("md5", md5)

        val chunkBody = UriChunkRequestBody(Globals.app.contentResolver, uri, offset, length)
        multipartBuilder.addFormDataPart("chunk", fileName, chunkBody)

        val request = Request.Builder().url(url).post(multipartBuilder.build()).build()
        val resp = request.awaitHttpResultStr(httpClient) ?: "{}"
        logdNoFile { "uploadChunk resp: $resp" }
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
        lastModified: Long
    ): String = withIOThread {
        val json = JSONObject().apply {
            put("md5", md5)
            put("fileName", fileName)
            put("totalChunks", totalChunks)
            put("lastModified", lastModified)
        }.toString()
        val resp = postJson("$baseUrl/merge-chunks", json)
        val bean = resp.fromGson<ResultBean<Unit>>() ?: throw IllegalStateException("parse mergeChunks resp failed: $resp")
        ensureSuc(bean.code, bean.msg)
        resp
    }

    /** POST /abort-upload-chunks JSON。 */
    suspend fun abortUploadChunks(baseUrl: String, md5: String, fileName: String): String = withIOThread {
        val json = JSONObject().apply {
            put("md5", md5)
            put("fileName", fileName)
        }.toString()
        val resp = postJson("$baseUrl/abort-upload-chunks", json)
        val bean = resp.fromGson<ResultBean<Unit>>() ?: throw IllegalStateException("parse abort resp failed: $resp")
        ensureSuc(bean.code, bean.msg)
        resp
    }

    /** 拼下载 URL 字符串。实际下载由 DownloadService 用 OkHttp 流式 GET 执行。 */
    fun downloadFileUrl(ip: String, httpPort: Int, uriUuid: String): String {
        return "http://$ip:$httpPort/file_download_uuid/$uriUuid"
    }

    private suspend fun postJson(url: String, json: String): String = withIOThread {
        val request = Request.Builder()
            .url(url)
            .post(json.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        request.awaitHttpResultStr(httpClient) ?: "{}"
    }

    private fun ensureSuc(code: String?, msg: String?) {
        if (code != CODE_SUC) {
            throw ClientApiException(code ?: "null", msg ?: "unknown error")
        }
    }
}
