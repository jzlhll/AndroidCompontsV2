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
import com.allan.mydroid.nanohttp.jsonResponse
import com.allan.mydroid.nanohttp.okJsonResponse
import com.allan.mydroid.state.GlobalReceiverFlowsObj
import com.allan.mydroid.globals.nanoTempCacheChunksDir
import com.au.module_android.Globals
import com.au.module_okhttp.api.ResultBean
import com.au.module_android.log.ALogJ
import com.au.module_android.log.logd
import com.au.module_android.log.logt
import com.au.module_android.utils.Md5Util.Companion.getFileMD5
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.Response.Status
import org.json.JSONObject
import java.io.File
import com.allan.mydroid.repository.TransferFiles
import com.au.module_android.log.logEx

class ChunkUploadHandler(private val receiverFlowsObj: GlobalReceiverFlowsObj) : AbsHttpRequestHandler() {

    override fun tryHandle(method: NanoHTTPD.Method, uri: String, session: IHTTPSession): Response? {
        if (method != NanoHTTPD.Method.POST) return null
        return when (uri) {
            UPLOAD_CHUNK -> handleUploadChunk(session)
            MERGE_CHUNKS -> handleMergeChunk(session)
            ABORT_UPLOAD_CHUNKS -> handleAbortChunk(session)
            else -> null
        }
    }
    /**
     * key 按来源地址、文件名与 MD5 隔离
     * value是chunkInfo组
     */
    private val fileChunkInfosMap = HashMap<String, ArrayList<ChunkInfoResult>>()
    /**
     * 用于加锁上面的操作
     */
    private val cLock = Any()

    private fun addChunkInfo(source: String, chunkInfo: ChunkInfoResult) {
        synchronized(cLock) {
            val fileName = chunkInfo.fileName
            val md5 = chunkInfo.md5
            val chunkInfoList = fileChunkInfosMap.getOrPut("$source:$fileName-$md5") {
                ArrayList()
            }
            chunkInfoList.removeAll {
                if (it.chunkIndex == chunkInfo.chunkIndex) {
                    it.chunkTmpFile.delete()
                    true
                } else false
            }
            chunkInfoList.add(chunkInfo)
        }
    }

    private fun removeChunkInfoList(source: String, fileName: String, md5:String) : ArrayList<ChunkInfoResult>? {
        synchronized(cLock) {
            return fileChunkInfosMap.remove("$source:$fileName-$md5")
        }
    }

    fun handleUploadChunk(session: IHTTPSession) : Response {
        var fileName = ""
        var chunkIndex:Int = -1
        var totalChunks = 0
        var md5 = ""

        try {
            // 1. 获取普通参数
            val parseBodyFileMap = HashMap<String, String>()
            session.parseBody(parseBodyFileMap)

            val params = session.parameters
            fileName = params["fileName"]?.first() ?: ""
            chunkIndex = params["chunkIndex"]?.first()?.toInt() ?: 0
            totalChunks = params["totalChunks"]?.first()?.toInt() ?: 0
            md5 = params["md5"]?.first() ?: ""

            require(fileName.isNotBlank() && totalChunks > 0 && chunkIndex in 1..totalChunks)
            require(md5.matches(Regex("[a-fA-F0-9]{32}")))
            // 2. 获取文件块内容（核心）
            val tmpFileStr = parseBodyFileMap["chunk"]
            if (tmpFileStr.isNullOrEmpty()) {
                val s = Globals.getString(R.string.file_not_received)
                return ResultBean<ChunkInfoResult>(CODE_FAIL, s, null).badRequestJsonResponse()
            }

            val tmpFile = File(tmpFileStr)
            logt{ "chunk: $fileName $md5, $chunkIndex/$totalChunks $tmpFileStr ${tmpFile.length()}" }

            // 3. 将临时文件转存 否则框架立刻clear掉了。
            val chunkTmpFileStr = nanoTempCacheChunksDir() + File.separatorChar + tmpFile.name
            val chunkTmpFile = File(chunkTmpFileStr)
            if (chunkTmpFile.exists()) {
                chunkTmpFile.delete()
            }
            check(tmpFile.renameTo(chunkTmpFile)) { "Cannot retain chunk" }

            val chunkInfo = ChunkInfoResult(fileName, chunkIndex, totalChunks, md5, chunkTmpFile)
            addChunkInfo(session.remoteIpAddress, chunkInfo)

            receiverFlowsObj.emitProgress(
                mapOf(
                    "$fileName-$md5" to ReceivingFileInfo(
                        fileName,
                        md5,
                        chunkIndex,
                        totalChunks,
                        PROCESS_CHUNK
                    )
                )
            )

            return ResultBean(
                CODE_SUC,
                "$fileName Chunk $chunkIndex/$totalChunks received success.", chunkInfo).okJsonResponse()
        } catch (e: Exception) {
            logd { ALogJ.ex(e) }
            receiverFlowsObj.emitProgress(
                mapOf(
                    "$fileName-$md5" to ReceivingFileInfo(
                        fileName,
                        md5,
                        chunkIndex,
                        totalChunks,
                        PROCESS_CHUNK_ERROR,
                        Globals.getString(R.string.chunk_receiver_error)
                    )
                )
            )
            return ResultBean<ChunkInfoResult>(
                CODE_FAIL_RECEIVER_CHUNK,
                "$fileName Chunk $chunkIndex/$totalChunks received failed!",
                null).okJsonResponse()
        }
    }

    fun handleMergeChunk(session: IHTTPSession): Response {
        val body = parseRequestBody(session)
        val params = JSONObject(body)
        // 3. 提取关键参数
        val md5 = params.optString("md5")
        val fileName = params.optString("fileName")
        val totalChunks = params.optInt("totalChunks")
        val lastModified = params.optLong("lastModified", System.currentTimeMillis())
        if (md5.isNullOrEmpty() || fileName.isNullOrEmpty() || totalChunks <= 0) {
            return ResultBean<ChunkInfoResult>(
                CODE_FAIL,
                Globals.getString(R.string.error_merge_chunk_params), null).badRequestJsonResponse()
        }
        logt { "handle Merge Chunk $fileName , $md5 , totalChunks:$totalChunks" }

        receiverFlowsObj.emitProgress(
            mapOf(
                "$fileName-$md5" to ReceivingFileInfo(
                    fileName,
                    md5,
                    totalChunks,
                    totalChunks,
                    PROCESS_MERGING
                )
            )
        )

        val chunkInfoList = removeChunkInfoList(session.remoteIpAddress, fileName, md5)
        if (chunkInfoList == null) {
            val noChunkStr = Globals.getString(R.string.no_chunks)

            receiverFlowsObj.emitProgress(
                mapOf(
                    "$fileName-$md5" to ReceivingFileInfo(
                        fileName,
                        md5,
                        totalChunks,
                        totalChunks,
                        PROCESS_MERGE_ERROR,
                        noChunkStr
                    )
                )
            )

            return ResultBean<ChunkInfoResult>(CODE_FAIL_MERGE_CHUNK, noChunkStr, null).jsonResponse(Status.OK)
        }
        chunkInfoList.sortBy { it.chunkIndex }
        if (chunkInfoList.size != totalChunks) {
            chunkInfoList.forEach { it.chunkTmpFile.delete() }
            val chunkNumNotMatchStr = Globals.getString(R.string.chunks_number_not_match)
            receiverFlowsObj.emitProgress(
                mapOf(
                    "$fileName-$md5" to ReceivingFileInfo(
                        fileName,
                        md5,
                        totalChunks,
                        totalChunks,
                        PROCESS_MERGE_ERROR,
                        chunkNumNotMatchStr
                    )
                )
            )
            return ResultBean<ChunkInfoResult>(CODE_FAIL_MERGE_CHUNK, chunkNumNotMatchStr, null).jsonResponse(Status.OK)
        }
        var temp: File? = null
        var errorCode = CODE_FAIL_MERGE_CHUNK
        try {
            if (chunkInfoList.withIndex().any { (index, chunk) ->
                    chunk.chunkIndex != index + 1 || chunk.totalChunks != totalChunks
                }) {
                throw IllegalStateException(Globals.getString(R.string.chunks_number_not_match))
            }
            val stagingFile = TransferFiles.temporaryFile()
            temp = stagingFile
            stagingFile.outputStream().use { output ->
                chunkInfoList.forEach { chunk ->
                    chunk.chunkTmpFile.inputStream().use { it.copyTo(output) }
                }
            }
            if (!getFileMD5(stagingFile.absolutePath).equals(md5, ignoreCase = true)) {
                errorCode = CODE_FAIL_MD5_CHECK
                throw IllegalStateException(Globals.getString(R.string.md5_check_failed))
            }
            stagingFile.setLastModified(lastModified)
            val outputFile = TransferFiles.publish(stagingFile, fileName)
            receiverFlowsObj.emitFileMerged(outputFile)
            receiverFlowsObj.emitProgress(mapOf("$fileName-$md5" to ReceivingFileInfo(
                fileName, md5, totalChunks, totalChunks, PROCESS_COMPLETED
            )))
            return ResultBean(CODE_SUC, Globals.getString(R.string.file_merge_success),
                mapOf("fileName" to outputFile.name)).okJsonResponse()
        } catch (e: Exception) {
            logEx(throwable = e) { "Merge chunks failed" }
            receiverFlowsObj.emitProgress(mapOf("$fileName-$md5" to ReceivingFileInfo(
                fileName, md5, totalChunks, totalChunks, PROCESS_MERGE_ERROR, e.message
            )))
            return ResultBean<String>(errorCode, e.message, null).okJsonResponse()
        } finally {
            temp?.delete()
            chunkInfoList.forEach { it.chunkTmpFile.delete() }
        }
    }

    fun handleAbortChunk(session: IHTTPSession): Response {
        logt { "clear up when abort chunk." }
        val body = parseRequestBody(session)
        val params = JSONObject(body)
        // 3. 提取关键参数
        val fileName = params.optString("fileName")
        val md5 = params.optString("md5")

        removeChunkInfoList(session.remoteIpAddress, fileName, md5)?.forEach { chunkInfo->
            chunkInfo.chunkTmpFile.delete() // 删除已合并的分片
        }
        return ResultBean<String>(
            CODE_SUC,
            Globals.getString(R.string.clear_up), null).jsonResponse(Status.OK)
    }

    // 辅助方法：将请求体转为字符串
    private fun parseRequestBody(session: IHTTPSession): String {
        val files = HashMap<String, String>()
        session.parseBody(files)
        return files["postData"] ?: "{}"
    }
}
