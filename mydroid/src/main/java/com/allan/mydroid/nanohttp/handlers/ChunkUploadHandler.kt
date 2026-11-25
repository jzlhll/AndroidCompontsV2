package com.allan.mydroid.nanohttp.handlers

import com.allan.mydroid.R
import com.allan.mydroid.beans.httpdata.ChunkInfoResult
import com.allan.mydroid.nanohttp.CODE_FAIL_MD5_CHECK
import com.allan.mydroid.nanohttp.CODE_FAIL_MERGE_CHUNK
import com.au.module_android.Globals
import com.au.module_android.log.logdNoFile
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.DigestOutputStream
import java.security.MessageDigest

/** 接收分片并校验合并后的文件，由调用方发布合并结果。 */
class ChunkUploadHandler(
    private val chunksDir: File,
    private val stagingDir: File,
    private val requiredExtension: String? = null,
) {
    class InvalidFileNameException : IllegalArgumentException("Invalid file name")

    sealed interface ChunkResult {
        data class Received(val key: String, val chunk: ChunkInfoResult) : ChunkResult
        data class Failed(
            val key: String,
            val fileName: String,
            val totalChunks: Int,
            val error: Exception,
        ) : ChunkResult
    }

    data class MergeRequest(
        val key: String,
        val fileName: String,
        val totalChunks: Int,
        val md5: String,
        val lastModified: Long,
        val streaming: Boolean,
        val receivedCount: Int,
    )

    data class AbortedUpload(val key: String, val fileName: String, val receivedCount: Int, val totalChunks: Int)

    sealed interface MergeResult {
        data class Merged(val temporaryFile: File, val md5: String) : MergeResult
        data class Failed(val code: String, val message: String?, val error: Exception? = null, val receivedCount: Int = 0) : MergeResult
    }

    private class UploadChunks(val totalChunks: Int) {
        val byIndex = HashMap<Int, ChunkInfoResult>()
    }

    // 流式协议使用上传会话索引，旧协议仍按完整 MD5 隔离。
    private val uploads = HashMap<String, UploadChunks>()
    private val closedUploads = LinkedHashSet<String>()
    private val cLock = Any()
    private var closed = false

    fun handleUploadChunk(session: IHTTPSession): ChunkResult {
        var fileName = ""
        var totalChunks = 0
        var key = ""
        var retainedFile: File? = null
        try {
            val files = HashMap<String, String>()
            session.parseBody(files)
            val params = session.parameters
            fileName = params["fileName"]?.firstOrNull().orEmpty()
            validateFileName(fileName)
            val chunkIndex = params["chunkIndex"]?.firstOrNull()?.toInt() ?: 0
            totalChunks = params["totalChunks"]?.firstOrNull()?.toInt() ?: 0
            val md5 = params["md5"]?.firstOrNull().orEmpty()
            val hashMode = params["hashMode"]?.firstOrNull().orEmpty()
            key = uploadKey(session, params["uploadId"]?.firstOrNull().orEmpty(), fileName, md5, hashMode)
            require(totalChunks > 0 && chunkIndex in 1..totalChunks) { "Invalid chunk index" }
            val path = files["chunk"]
            require(!path.isNullOrEmpty()) { Globals.getString(R.string.file_not_received) }
            val source = File(path)
            val result = synchronized(cLock) {
                check(!closed && key !in closedUploads) { "Upload session is closed" }
                val existing = uploads[key]
                check(existing == null || existing.totalChunks == totalChunks) { "Chunk count changed" }
                ensureDirectory(chunksDir)
                val target = File(chunksDir, source.name)
                check(source.renameTo(target)) { "Cannot retain chunk" }
                retainedFile = target
                val upload = existing ?: UploadChunks(totalChunks).also { uploads[key] = it }
                val chunk = ChunkInfoResult(fileName, chunkIndex, totalChunks, if (hashMode == "stream") "" else md5, target)
                upload.byIndex.put(chunkIndex, chunk)?.chunkTmpFile?.delete()
                ChunkResult.Received(key, chunk)
            }
            retainedFile = null
            logdNoFile {
                "receive chunk $fileName $chunkIndex/$totalChunks bytes=${result.chunk.chunkTmpFile.length()} hashMode=$hashMode"
            }
            return result
        } catch (e: Exception) {
            retainedFile?.delete()
            return ChunkResult.Failed(key, fileName, totalChunks, e)
        }
    }

    fun retainedCount(result: ChunkResult.Received): Int? = synchronized(cLock) {
        val chunks = uploads[result.key]?.byIndex ?: return@synchronized null
        if (chunks[result.chunk.chunkIndex] === result.chunk) chunks.size else null
    }

    fun isUploadClosed(key: String): Boolean = synchronized(cLock) { closed || key in closedUploads }

    fun readMergeRequest(session: IHTTPSession, params: JSONObject): MergeRequest? {
        val md5 = params.optString("md5")
        val fileName = params.optString("fileName")
        validateFileName(fileName)
        val totalChunks = params.optInt("totalChunks")
        val lastModified = params.optLong("lastModified", System.currentTimeMillis())
        if (!md5.matches(Regex("[a-fA-F0-9]{32}")) || fileName.isBlank() || totalChunks <= 0) return null

        val hashMode = params.optString("hashMode")
        val key = uploadKey(session, params.optString("uploadId"), fileName, md5, hashMode)
        val receivedCount = synchronized(cLock) { uploads[key]?.byIndex?.size ?: 0 }
        return MergeRequest(key, fileName, totalChunks, md5, lastModified, hashMode == "stream", receivedCount)
    }

    fun handleMergeChunk(request: MergeRequest): MergeResult {
        val upload = removeUpload(request.key, request.streaming)
            ?: return MergeResult.Failed(CODE_FAIL_MERGE_CHUNK, Globals.getString(R.string.no_chunks))
        val chunks = upload.byIndex
        var temp: File? = null
        var errorCode = CODE_FAIL_MERGE_CHUNK
        try {
            val totalChunks = request.totalChunks
            check(upload.totalChunks == totalChunks && chunks.size == totalChunks) {
                Globals.getString(R.string.chunks_number_not_match)
            }
            val digest = MessageDigest.getInstance("MD5")
            val stagingFile = synchronized(cLock) {
                check(!closed) { "Upload session is closed" }
                ensureDirectory(stagingDir)
                File.createTempFile("transfer-", ".${requiredExtension ?: "part"}", stagingDir)
            }
            temp = stagingFile
            // 合并写入时计算完整摘要，避免再次读取整个文件。
            DigestOutputStream(stagingFile.outputStream().buffered(64 * 1024), digest).use { output ->
                for (index in 1..totalChunks) {
                    chunks.getValue(index).chunkTmpFile.inputStream().use { it.copyTo(output, 64 * 1024) }
                }
            }
            val actualMd5 = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actualMd5.equals(request.md5, ignoreCase = true)) {
                errorCode = CODE_FAIL_MD5_CHECK
                throw IllegalStateException(Globals.getString(R.string.md5_check_failed))
            }
            stagingFile.setLastModified(request.lastModified)
            temp = null
            return MergeResult.Merged(stagingFile, actualMd5)
        } catch (e: Exception) {
            return MergeResult.Failed(errorCode, e.message, e, chunks.size)
        } finally {
            temp?.delete()
            chunks.values.forEach { it.chunkTmpFile.delete() }
        }
    }

    fun handleAbortChunk(session: IHTTPSession, params: JSONObject): AbortedUpload {
        val hashMode = params.optString("hashMode")
        val fileName = params.optString("fileName")
        val key = uploadKey(session, params.optString("uploadId"), fileName, params.optString("md5"), hashMode)
        val upload = removeUpload(key, hashMode == "stream")
        upload?.byIndex?.values?.forEach { it.chunkTmpFile.delete() }
        return AbortedUpload(key, fileName, upload?.byIndex?.size ?: 0, upload?.totalChunks ?: 0)
    }

    fun close(): List<File> = synchronized(cLock) {
        closed = true
        val pendingFiles = uploads.values.flatMap { it.byIndex.values.map { chunk -> chunk.chunkTmpFile } }
        uploads.clear()
        closedUploads.clear()
        pendingFiles
    }

    private fun validateFileName(fileName: String) {
        val extension = requiredExtension ?: return
        if (fileName.length <= extension.length + 1 || '/' in fileName || '\\' in fileName ||
            !fileName.endsWith(".$extension", ignoreCase = true)
        ) {
            throw InvalidFileNameException()
        }
    }

    private fun uploadKey(session: IHTTPSession, uploadId: String, fileName: String, md5: String, hashMode: String): String {
        require(fileName.isNotBlank()) { "Invalid file name" }
        require(hashMode.isEmpty() || hashMode == "stream") { "Invalid hash mode" }
        if (uploadId.isNotEmpty()) {
            require(uploadId.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Invalid upload session" }
        }
        if (hashMode == "stream") {
            require(uploadId.isNotEmpty()) { "Missing upload session" }
            return "${session.remoteIpAddress}:stream:$uploadId:$fileName"
        }
        require(md5.matches(Regex("[a-fA-F0-9]{32}"))) { "Invalid MD5" }
        return "${session.remoteIpAddress}:$uploadId:$fileName-${md5.lowercase()}"
    }

    private fun removeUpload(key: String, closeSession: Boolean): UploadChunks? = synchronized(cLock) {
        if (closed) return@synchronized null
        // 避免取消或合并后迟到的分块产生孤立临时文件。
        if (closeSession) {
            closedUploads.add(key)
            while (closedUploads.size > 256) closedUploads.remove(closedUploads.first())
        }
        uploads.remove(key)
    }

    private fun ensureDirectory(dir: File) {
        if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) throw IOException("Cannot create upload directory")
    }
}
