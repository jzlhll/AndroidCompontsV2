package com.allan.mydroid.nanohttp.handlers

import com.allan.mydroid.beansinner.FROM_LOCAL
import com.allan.mydroid.beansinner.ShareInBean
import com.allan.mydroid.globals.nanoTempCacheMergedDir
import com.allan.mydroid.globals.IDroidServerAliveTrigger
import com.allan.mydroid.repository.GlobalShareInRepoObj
import com.allan.mydroid.repository.UriPermissionChecker
import com.au.module_android.Globals
import com.au.module_android.log.logdNoFile
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response
import org.koin.core.component.inject
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.FilterInputStream
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicBoolean
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import com.au.module_okhttp.api.ResultBean
import com.allan.mydroid.nanohttp.CODE_SUC
import com.allan.mydroid.nanohttp.okJsonResponse

/**
 * 处理 GET /file_download_uuid/{uuid} 文件下载请求。
 */
class FileDownloadHandler(private val aliveTrigger: IDroidServerAliveTrigger) : AbsHttpRequestHandler() {

    private val shareInRepository: GlobalShareInRepoObj by inject()
    private val uriPermissionChecker: UriPermissionChecker by inject()

    override fun tryHandle(method: NanoHTTPD.Method, uri: String, session: IHTTPSession): Response? {
        if (method == NanoHTTPD.Method.GET && uri.startsWith(STATUS_PREFIX)) {
            val id = uri.substring(STATUS_PREFIX.length)
            val result = shareInRepository.downloadTransfersFlow.value[id] ?: return fileNotFoundResponse()
            return ResultBean(CODE_SUC, "Success", result).okJsonResponse().also {
                it.addHeader("Cache-Control", "no-store")
            }
        }
        if (method == NanoHTTPD.Method.GET && uri.startsWith(URL_PREFIX)) {
            val transferId = session.parameters["transferId"]?.firstOrNull() ?: UUID.randomUUID().toString()
            if (!transferId.matches(Regex("[A-Za-z0-9_-]{1,64}"))) return newNotFoundResponse("Invalid download session")
            return fileDownload(uri.substring(URL_PREFIX.length), transferId)
        }
        return null
    }

    private fun fileDownload(uriUuid: String, transferId: String): Response {
        var transferStream: InputStream? = null
        var registered = false
        try {
            val info = shareInRepository.shareInAndReceiveBeans?.find { it.uriUuid == uriUuid }
                ?: return fileNotFoundResponse()
            val fileSize = info.fileSize ?: 0
            if (fileSize <= 0) {
                return fileSizeIs0Response()
            }
            val uri = info.uri
            val filename = info.name ?: "file"
            logdNoFile { "file Download1 $uri size:$fileSize" }

            if (!uriPermissionChecker.isHostThisUri(info)) {
                logdNoFile { "file Download this uri is donot has permission." }
                return newInternalErrorResponse("No permission yet todo translate.")
            }
            val inputStream = openDownloadInputStream(info)
            aliveTrigger.transferStarted()
            val digest = MessageDigest.getInstance("MD5")
            var readBytes = 0L
            // 摘要仅覆盖实际发送的字节；下载仍由浏览器原生流式落盘。
            val trackedStream = object : FilterInputStream(inputStream) {
                private val closed = AtomicBoolean(false)
                override fun read(): Int {
                    val value = inputStream.read()
                    if (value >= 0) { digest.update(value.toByte()); readBytes++ }
                    return value
                }
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    val count = inputStream.read(buffer, offset, length)
                    if (count > 0) { digest.update(buffer, offset, count); readBytes += count }
                    return count
                }
                override fun close() {
                    if (!closed.compareAndSet(false, true)) return
                    try {
                        super.close()
                    } finally {
                        aliveTrigger.transferFinished()
                    }
                }
            }
            transferStream = trackedStream
            shareInRepository.startDownload(transferId, uriUuid)
            registered = true
            val response = object : Response(Response.Status.OK, "application/octet-stream", trackedStream, fileSize) {
                private val published = AtomicBoolean(false)

                private fun publishResult(success: Boolean) {
                    if (!published.compareAndSet(false, true)) return
                    val md5 = if (success) digest.digest().joinToString("") { "%02x".format(it) } else null
                    shareInRepository.finishDownload(transferId, md5)
                }

                override fun send(outputStream: OutputStream) {
                    var failed = false
                    var returned = false
                    // NanoHTTPD 内部捕获写入异常，这里保留失败状态，避免断线被误报为完成。
                    val checkedOutput = object : OutputStream() {
                        override fun write(value: Int) {
                            try { outputStream.write(value) }
                            catch (e: IOException) { failed = true; throw e }
                        }
                        override fun write(buffer: ByteArray, offset: Int, length: Int) {
                            try { outputStream.write(buffer, offset, length) }
                            catch (e: IOException) { failed = true; throw e }
                        }
                        override fun flush() {
                            try { outputStream.flush() }
                            catch (e: IOException) { failed = true; throw e }
                        }
                    }
                    try {
                        super.send(checkedOutput)
                        returned = true
                    } finally {
                        try { trackedStream.close() }
                        finally { publishResult(returned && !failed && readBytes == fileSize) }
                    }
                }

                override fun close() {
                    try { super.close() }
                    finally { publishResult(false) }
                }
            }
            logdNoFile { "file response1111" }
            // 2. 设置 Content-Disposition 头，这是触发浏览器下载的关键
            val encodedFileName = URLEncoder.encode(filename, "UTF-8")
                .replace("\\+".toRegex(), "%20") // 替换空格编码
            val fallbackFileName = filename.map { character ->
                if (character !in ' '..'~' || character == '"' || character == '\\') '_'
                else character
            }.joinToString("")
            response.addHeader(
                "Content-Disposition",
                "attachment; filename=\"" +
                        fallbackFileName + "\"; " +
                        "filename*=UTF-8''" + encodedFileName
            )
            // 禁止浏览器或代理缓存大文件响应。
            response.addHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
            response.addHeader("Pragma", "no-cache")
            response.addHeader("Expires", "0")

            // 3. （可选但推荐）设置 Content-Length 头
            response.addHeader("Content-Length", "" + fileSize)
            // 4. （可选）设置 Content-Type，如果你确切知道文件类型，可以设置更具体的 MIME 类型
            transferStream = null
            return response
        } catch (e: FileNotFoundException) {
            logdNoFile { "file Download error1 ${e.message}" }
            return newInternalErrorResponse("Error reading file 1.")
        } catch (e: IOException) {
            logdNoFile { "file Download error2 ${e.message}" }
            return newInternalErrorResponse("Error reading file 2.")
        } catch (e: Exception) {
            logdNoFile { "file Download error3 ${e.message}" }
            return newInternalErrorResponse("Error reading file 3.")
        } finally {
            if (transferStream != null && registered) shareInRepository.finishDownload(transferId, null)
            transferStream?.close()
        }
    }

    /** nanoMerged 本地文件直接用绝对路径读取，避免 file:// Uri 解析问题 */
    private fun openDownloadInputStream(info: ShareInBean): InputStream {
        if (info.from == FROM_LOCAL) {
            val name = info.name
            if (name.isNullOrEmpty()) {
                throw FileNotFoundException("local merged file name empty")
            }
            val file = File(nanoTempCacheMergedDir(), name)
            if (!file.exists()) {
                throw FileNotFoundException("local merged file not exists: ${file.absolutePath}")
            }
            return FileInputStream(file)
        }
        val stream = Globals.app.contentResolver.openInputStream(info.uri)
            ?: throw FileNotFoundException("Cannot open uri: ${info.uri}")
        return stream
    }

    private fun fileNotFoundResponse(): Response {
        return newNotFoundResponse("File not found.")
    }

    private fun fileSizeIs0Response(): Response {
        return newNotFoundResponse("File size is 0.")
    }

    companion object {
        private const val URL_PREFIX = "/file_download_uuid/"
        private const val STATUS_PREFIX = "/file_download_status/"
    }
}
