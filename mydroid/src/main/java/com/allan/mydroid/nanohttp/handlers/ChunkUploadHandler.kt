package com.allan.mydroid.nanohttp.handlers

import com.allan.mydroid.R
import com.allan.mydroid.api.ABORT_UPLOAD_CHUNKS
import com.allan.mydroid.api.MERGE_CHUNKS
import com.allan.mydroid.api.UPLOAD_CHUNK
import com.allan.mydroid.api.WSApisConst2.PROCESS_CHUNK
import com.allan.mydroid.api.WSApisConst2.PROCESS_CHUNK_ERROR
import com.allan.mydroid.api.WSApisConst2.PROCESS_COMPLETED
import com.allan.mydroid.api.WSApisConst2.PROCESS_MERGE_ERROR
import com.allan.mydroid.api.WSApisConst2.PROCESS_MERGING
import com.allan.mydroid.beans.httpdata.ChunkInfoResult
import com.allan.mydroid.beansinner.ReceivingFileInfo
import com.allan.mydroid.nanohttp.CODE_FAIL
import com.allan.mydroid.nanohttp.CODE_FAIL_MD5_CHECK
import com.allan.mydroid.nanohttp.CODE_FAIL_MERGE_CHUNK
import com.allan.mydroid.nanohttp.CODE_FAIL_RECEIVER_CHUNK
import com.allan.mydroid.nanohttp.CODE_SUC
import com.allan.mydroid.nanohttp.badRequestJsonResponse
import com.allan.mydroid.nanohttp.okJsonResponse
import com.allan.mydroid.state.GlobalReceiverFlowsObj
import com.allan.mydroid.globals.nanoTempCacheChunksDir
import com.au.module_android.Globals
import com.au.module_okhttp.api.ResultBean
import java.security.MessageDigest
import java.security.DigestOutputStream
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response
import org.json.JSONObject
import java.io.File
import com.allan.mydroid.repository.TransferFiles
import com.au.module_android.log.logdNoFile
import com.au.module_android.log.logEx
import com.allan.mydroid.globals.IDroidServerAliveTrigger
import com.allan.mydroid.repository.GlobalFileListRepoObj
import kotlinx.coroutines.runBlocking
import org.koin.core.component.inject

/** 接收、校验并发布分片文件，传输期间阻止主机空闲退出。 */
class ChunkUploadHandler(
    private val receiverFlowsObj: GlobalReceiverFlowsObj,
    private val aliveTrigger: IDroidServerAliveTrigger
) : AbsHttpRequestHandler() {
    private val fileListRepository: GlobalFileListRepoObj by inject()

    override fun tryHandle(method: NanoHTTPD.Method, uri: String, session: IHTTPSession): Response? {
        if (method != NanoHTTPD.Method.POST ||
            (uri != UPLOAD_CHUNK && uri != MERGE_CHUNKS && uri != ABORT_UPLOAD_CHUNKS)) return null
        aliveTrigger.transferStarted()
        return try {
            when (uri) {
                UPLOAD_CHUNK -> handleUploadChunk(session)
                MERGE_CHUNKS -> handleMergeChunk(session)
                else -> handleAbortChunk(session)
            }
        } finally {
            aliveTrigger.transferFinished()
        }
    }
    // 流式协议使用上传会话索引；旧协议仍按完整 MD5 隔离。
    private val fileChunkInfosMap = HashMap<String, ArrayList<ChunkInfoResult>>()
    private val closedUploads = LinkedHashSet<String>()
    private val cLock = Any()

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

    private fun removeChunkInfoList(key: String, closeSession: Boolean): ArrayList<ChunkInfoResult>? = synchronized(cLock) {
        // 拒绝取消或合并后迟到的分块，防止清理完成后又产生孤立临时文件。
        if (closeSession) {
            closedUploads.add(key)
            while (closedUploads.size > 256) closedUploads.remove(closedUploads.first())
        }
        fileChunkInfosMap.remove(key)
    }

    fun handleUploadChunk(session: IHTTPSession): Response {
        var fileName = ""
        var chunkIndex = 0
        var totalChunks = 0
        var key = ""
        var retainedFile: File? = null
        try {
            val files = HashMap<String, String>()
            session.parseBody(files)
            val params = session.parameters
            fileName = params["fileName"]?.firstOrNull().orEmpty()
            chunkIndex = params["chunkIndex"]?.firstOrNull()?.toInt() ?: 0
            totalChunks = params["totalChunks"]?.firstOrNull()?.toInt() ?: 0
            val md5 = params["md5"]?.firstOrNull().orEmpty()
            val hashMode = params["hashMode"]?.firstOrNull().orEmpty()
            key = uploadKey(session, params["uploadId"]?.firstOrNull().orEmpty(), fileName, md5, hashMode)
            require(totalChunks > 0 && chunkIndex in 1..totalChunks) { "Invalid chunk index" }
            val path = files["chunk"]
            require(!path.isNullOrEmpty()) { Globals.getString(R.string.file_not_received) }
            val source = File(path)
            val target = File(nanoTempCacheChunksDir(), source.name)
            check(source.renameTo(target)) { "Cannot retain chunk" }
            retainedFile = target
            val chunk = ChunkInfoResult(fileName, chunkIndex, totalChunks, if (hashMode == "stream") "" else md5, target)
            synchronized(cLock) {
                check(key !in closedUploads) { "Upload session is closed" }
                val list = fileChunkInfosMap.getOrPut(key) { ArrayList() }
                check(list.all { it.totalChunks == totalChunks }) { "Chunk count changed" }
                list.removeAll {
                    if (it.chunkIndex == chunkIndex) { it.chunkTmpFile.delete(); true } else false
                }
                list.add(chunk)
            }
            retainedFile = null
            logdNoFile { "receive chunk $fileName $chunkIndex/$totalChunks bytes=${target.length()} hashMode=$hashMode" }
            receiverFlowsObj.emitProgress(mapOf(key to ReceivingFileInfo(
                fileName, "", chunkIndex, totalChunks, PROCESS_CHUNK
            )))
            return ResultBean(CODE_SUC, "Chunk received", chunk).okJsonResponse()
        } catch (e: Exception) {
            retainedFile?.delete()
            logEx(throwable = e) { "Receive chunk failed" }
            receiverFlowsObj.emitProgress(mapOf(key to ReceivingFileInfo(
                fileName, "", chunkIndex, totalChunks, PROCESS_CHUNK_ERROR,
                Globals.getString(R.string.chunk_receiver_error)
            )))
            return ResultBean<String>(CODE_FAIL_RECEIVER_CHUNK, e.message, null).okJsonResponse().also {
                it.addHeader("Connection", "close")
            }
        }
    }

    fun handleMergeChunk(session: IHTTPSession): Response {
        val params = JSONObject(parseRequestBody(session))
        val md5 = params.optString("md5")
        val fileName = params.optString("fileName")
        val totalChunks = params.optInt("totalChunks")
        val lastModified = params.optLong("lastModified", System.currentTimeMillis())
        if (!md5.matches(Regex("[a-fA-F0-9]{32}")) || fileName.isBlank() || totalChunks <= 0) {
            return ResultBean<String>(CODE_FAIL, Globals.getString(R.string.error_merge_chunk_params), null).badRequestJsonResponse()
        }
        val key = uploadKey(session, params.optString("uploadId"), fileName, md5, params.optString("hashMode"))
        receiverFlowsObj.emitProgress(mapOf(key to ReceivingFileInfo(
            fileName, "", totalChunks, totalChunks, PROCESS_MERGING
        )))
        val chunks = removeChunkInfoList(key, params.optString("hashMode") == "stream")
        if (chunks == null) {
            val message = Globals.getString(R.string.no_chunks)
            receiverFlowsObj.emitProgress(mapOf(key to ReceivingFileInfo(
                fileName, "", totalChunks, totalChunks, PROCESS_MERGE_ERROR, message
            )))
            return ResultBean<String>(CODE_FAIL_MERGE_CHUNK, message, null).okJsonResponse()
        }
        var temp: File? = null
        var errorCode = CODE_FAIL_MERGE_CHUNK
        try {
            chunks.sortBy { it.chunkIndex }
            check(chunks.size == totalChunks && chunks.withIndex().all { (index, chunk) ->
                chunk.chunkIndex == index + 1 && chunk.totalChunks == totalChunks
            }) { Globals.getString(R.string.chunks_number_not_match) }
            val digest = MessageDigest.getInstance("MD5")
            val stagingFile = TransferFiles.temporaryFile()
            temp = stagingFile
            // 合并写入时计算完整摘要，避免合并后再次读取整个文件。
            DigestOutputStream(stagingFile.outputStream().buffered(64 * 1024), digest).use { output ->
                chunks.forEach { chunk -> chunk.chunkTmpFile.inputStream().use { it.copyTo(output, 64 * 1024) } }
            }
            val actualMd5 = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actualMd5.equals(md5, ignoreCase = true)) {
                errorCode = CODE_FAIL_MD5_CHECK
                throw IllegalStateException(Globals.getString(R.string.md5_check_failed))
            }
            stagingFile.setLastModified(lastModified)
            val outputFile = TransferFiles.publish(stagingFile, fileName)
            runBlocking { fileListRepository.addReceivedFile(outputFile, actualMd5) }
            receiverFlowsObj.emitFileMerged(outputFile)
            receiverFlowsObj.emitProgress(mapOf(key to ReceivingFileInfo(
                fileName, actualMd5, totalChunks, totalChunks, PROCESS_COMPLETED
            )))
            return ResultBean(CODE_SUC, Globals.getString(R.string.file_merge_success),
                mapOf("fileName" to outputFile.name, "md5" to actualMd5)).okJsonResponse()
        } catch (e: Exception) {
            logEx(throwable = e) { "Merge chunks failed" }
            receiverFlowsObj.emitProgress(mapOf(key to ReceivingFileInfo(
                fileName, "", totalChunks, totalChunks, PROCESS_MERGE_ERROR, e.message
            )))
            return ResultBean<String>(errorCode, e.message, null).okJsonResponse()
        } finally {
            temp?.delete()
            chunks.forEach { it.chunkTmpFile.delete() }
        }
    }

    fun handleAbortChunk(session: IHTTPSession): Response {
        val params = JSONObject(parseRequestBody(session))
        val key = uploadKey(session, params.optString("uploadId"), params.optString("fileName"),
            params.optString("md5"), params.optString("hashMode"))
        removeChunkInfoList(key, params.optString("hashMode") == "stream")?.forEach { it.chunkTmpFile.delete() }
        return ResultBean<String>(CODE_SUC, Globals.getString(R.string.clear_up), null).okJsonResponse()
    }

    // 辅助方法：将请求体转为字符串
    private fun parseRequestBody(session: IHTTPSession): String {
        val files = HashMap<String, String>()
        session.parseBody(files)
        return files["postData"] ?: "{}"
    }
}
