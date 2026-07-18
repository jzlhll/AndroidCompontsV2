package com.allan.mydroid.nanohttp.handlers

import com.allan.mydroid.beansinner.FROM_LOCAL
import com.allan.mydroid.beansinner.ShareInBean
import com.allan.mydroid.globals.nanoTempCacheMergedDir
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
import java.net.URLEncoder

/**
 * 处理 GET /file_download_uuid/{uuid} 文件下载请求。
 */
class FileDownloadHandler : AbsHttpRequestHandler() {

    private val shareInRepository: GlobalShareInRepoObj by inject()
    private val uriPermissionChecker: UriPermissionChecker by inject()

    override fun tryHandle(method: NanoHTTPD.Method, uri: String, session: IHTTPSession): Response? {
        if (method == NanoHTTPD.Method.GET && uri.startsWith(URL_PREFIX)) {
            return fileDownload(uri.substring(URL_PREFIX.length))
        }
        return null
    }

    private fun fileDownload(uriUuid: String): Response {
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
            logdNoFile { "file Download2 ${info.from} $filename ${inputStream.available()}" }
            // 1. 创建响应，指定状态码为 OK，MIME 类型为二进制流（强制下载）
            val response = NanoHTTPD.newFixedLengthResponse(
                Response.Status.OK,
                "application/octet-stream", inputStream, fileSize)
            logdNoFile { "file response1111" }
            // 2. 设置 Content-Disposition 头，这是触发浏览器下载的关键
            val encodedFileName = URLEncoder.encode(filename, "UTF-8")
                .replace("\\+".toRegex(), "%20") // 替换空格编码
            response.addHeader(
                "Content-Disposition",
                "attachment; filename=\"" +
                        String(filename.toByteArray(charset("GBK")),
                            charset("ISO-8859-1")) + "\"; " +
                        "filename*=UTF-8''" + encodedFileName
            )
            // Avoid browser/proxy caching large binary downloads in memory or disk cache.
            response.addHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
            response.addHeader("Pragma", "no-cache")
            response.addHeader("Expires", "0")

            // 3. （可选但推荐）设置 Content-Length 头
            response.addHeader("Content-Length", "" + fileSize)
            // 4. （可选）设置 Content-Type，如果你确切知道文件类型，可以设置更具体的 MIME 类型
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
    }
}
