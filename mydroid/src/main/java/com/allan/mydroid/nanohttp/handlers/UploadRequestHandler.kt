package com.allan.mydroid.nanohttp.handlers

import com.allan.mydroid.R
import com.allan.mydroid.api.ABORT_UPLOAD_CHUNKS
import com.allan.mydroid.api.MERGE_CHUNKS
import com.allan.mydroid.api.UPLOAD_CHUNK
import com.allan.mydroid.api.WSApisConst2.PROCESS_ABORTED
import com.allan.mydroid.api.WSApisConst2.PROCESS_CHUNK
import com.allan.mydroid.api.WSApisConst2.PROCESS_CHUNK_ERROR
import com.allan.mydroid.api.WSApisConst2.PROCESS_COMPLETED
import com.allan.mydroid.api.WSApisConst2.PROCESS_MERGE_ERROR
import com.allan.mydroid.api.WSApisConst2.PROCESS_MERGING
import com.allan.mydroid.apk.ApkBuildInfo
import com.allan.mydroid.apk.ApkReceiveSession
import com.allan.mydroid.beansinner.ReceivingFileInfo
import com.allan.mydroid.globals.IDroidServerAliveTrigger
import com.allan.mydroid.nanohttp.CODE_FAIL
import com.allan.mydroid.nanohttp.CODE_FAIL_MERGE_CHUNK
import com.allan.mydroid.nanohttp.CODE_FAIL_RECEIVER_CHUNK
import com.allan.mydroid.nanohttp.CODE_SUC
import com.allan.mydroid.nanohttp.badRequestJsonResponse
import com.allan.mydroid.nanohttp.okJsonResponse
import com.allan.mydroid.repository.GlobalFileListRepoObj
import com.allan.mydroid.repository.TransferFiles
import com.allan.mydroid.state.GlobalReceiverFlowsObj
import com.au.module_android.Globals
import com.au.module_android.log.logEx
import com.au.module_okhttp.api.ResultBean
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.koin.core.component.inject

/** 将上传路由接到通用分块处理，并发布当前页面的接收结果。 */
class UploadRequestHandler(
    private val chunkUploadHandler: ChunkUploadHandler,
    private val aliveTrigger: IDroidServerAliveTrigger,
    private val apkSession: ApkReceiveSession?,
) : AbsHttpRequestHandler() {
    private val fileListRepository: GlobalFileListRepoObj by inject()
    private val receiverFlowsObj: GlobalReceiverFlowsObj by inject()

    override fun tryHandle(method: Method, uri: String, session: IHTTPSession): Response? {
        if (method != Method.POST) return null
        val route = if (apkSession == null) {
            uri
        } else {
            if (!uri.startsWith("/apk/")) return null
            uri.removePrefix("/apk")
        }
        if (route != UPLOAD_CHUNK && route != MERGE_CHUNKS && route != ABORT_UPLOAD_CHUNKS) return null

        aliveTrigger.transferStarted()
        return try {
            when (route) {
                UPLOAD_CHUNK -> {
                    apkSession?.changeActiveChunkRequests(1)
                    try {
                        handleUploadChunk(session)
                    } finally {
                        apkSession?.changeActiveChunkRequests(-1)
                    }
                }
                MERGE_CHUNKS -> handleMergeChunk(session)
                else -> {
                    val upload = chunkUploadHandler.handleAbortChunk(session, readRequestBody(session))
                    apkSession?.updateProgress(upload.key, ReceivingFileInfo(
                        upload.fileName, "", upload.receivedCount, upload.totalChunks, PROCESS_ABORTED,
                    ))
                    ResultBean<String>(CODE_SUC, Globals.getString(R.string.clear_up), null).okJsonResponse()
                }
            }
        } finally {
            aliveTrigger.transferFinished()
        }
    }

    private fun handleUploadChunk(session: IHTTPSession): Response {
        return when (val result = chunkUploadHandler.handleUploadChunk(session)) {
            is ChunkUploadHandler.ChunkResult.Received -> {
                val chunk = result.chunk
                if (apkSession != null) synchronized(apkSession) {
                    // 只发布仍属于当前分片集合的结果，合并、取消和重传可使旧结果失效。
                    val count = chunkUploadHandler.retainedCount(result)
                    if (count != null) apkSession.updateProgress(result.key, ReceivingFileInfo(
                        chunk.fileName, "", count, chunk.totalChunks, PROCESS_CHUNK,
                    ))
                }
                ResultBean(CODE_SUC, "Chunk received", chunk).okJsonResponse()
            }
            is ChunkUploadHandler.ChunkResult.Failed -> {
                val error = result.error
                logEx(throwable = error) { "Receive chunk failed" }
                if (apkSession != null && !chunkUploadHandler.isUploadClosed(result.key)) {
                    apkSession.updateProgress(result.key, ReceivingFileInfo(
                        result.fileName, "", 0,
                        result.totalChunks, PROCESS_CHUNK_ERROR,
                        Globals.getString(R.string.chunk_receiver_error),
                    ))
                }
                val message = if (apkSession != null && error is ChunkUploadHandler.InvalidFileNameException) {
                    Globals.getString(R.string.mydroid_apk_only)
                } else {
                    error.message
                }
                ResultBean<String>(CODE_FAIL_RECEIVER_CHUNK, message, null).okJsonResponse().also {
                    it.addHeader("Connection", "close")
                }
            }
        }
    }

    private fun handleMergeChunk(session: IHTTPSession): Response {
        val params = readRequestBody(session)
        val request = chunkUploadHandler.readMergeRequest(session, params) ?: return ResultBean<String>(
            CODE_FAIL, Globals.getString(R.string.error_merge_chunk_params), null
        ).badRequestJsonResponse()
        if (apkSession != null) {
            val progress = ReceivingFileInfo(request.fileName, "", request.receivedCount, request.totalChunks, PROCESS_MERGING)
            if (!apkSession.startMerging(request.key, progress)) {
                return ResultBean<String>(CODE_FAIL_MERGE_CHUNK, Globals.app.getString(R.string.no_chunks), null).okJsonResponse()
            }
        }
        val buildInfo = try {
            if (apkSession != null && params.has("buildInfo")) ApkBuildInfo.fromJson(params.getJSONObject("buildInfo")) else null
        } catch (e: Exception) {
            val upload = chunkUploadHandler.handleAbortChunk(session, params)
            return mergeFailure(request, CODE_FAIL_MERGE_CHUNK, Globals.getString(R.string.mydroid_apk_build_invalid), e,
                upload.receivedCount)
        }
        return when (val result = chunkUploadHandler.handleMergeChunk(request)) {
            is ChunkUploadHandler.MergeResult.Merged -> publish(request, result, buildInfo)
            is ChunkUploadHandler.MergeResult.Failed ->
                mergeFailure(request, result.code, result.message, result.error, result.receivedCount)
        }
    }

    private fun publish(
        request: ChunkUploadHandler.MergeRequest,
        result: ChunkUploadHandler.MergeResult.Merged,
        buildInfo: ApkBuildInfo?,
    ): Response {
        val temp = result.temporaryFile
        val md5 = result.md5
        return try {
            val data = if (apkSession != null) {
                val received = apkSession.publish(temp, request.fileName, buildInfo)
                mapOf("fileName" to received.file.name, "md5" to md5, "sha256" to received.buildInfo.sha256)
            } else {
                val file = TransferFiles.publish(temp, request.fileName)
                // 正式文件已发布，更新本地索引失败也应向发送端确认收件成功。
                try {
                    runBlocking { fileListRepository.addReceivedFile(file, md5) }
                } catch (e: Exception) {
                    logEx(throwable = e) { "Update published file index failed" }
                }
                receiverFlowsObj.emitFileMerged(file)
                mapOf("fileName" to file.name, "md5" to md5)
            }
            apkSession?.updateProgress(request.key, ReceivingFileInfo(
                request.fileName, md5, request.totalChunks, request.totalChunks, PROCESS_COMPLETED
            ))
            ResultBean(CODE_SUC, Globals.getString(R.string.file_merge_success), data).okJsonResponse()
        } catch (e: Exception) {
            mergeFailure(request, CODE_FAIL_MERGE_CHUNK, e.message, e, request.totalChunks)
        } finally {
            temp.delete()
        }
    }

    private fun readRequestBody(session: IHTTPSession): JSONObject {
        val files = HashMap<String, String>()
        session.parseBody(files)
        return JSONObject(files["postData"] ?: "{}")
    }

    private fun mergeFailure(
        request: ChunkUploadHandler.MergeRequest,
        code: String,
        message: String?,
        error: Exception?,
        receivedCount: Int = 0,
    ): Response {
        if (error != null) logEx(throwable = error) { "Merge chunks failed" }
        apkSession?.updateProgress(request.key, ReceivingFileInfo(
            request.fileName, "", receivedCount, request.totalChunks, PROCESS_MERGE_ERROR, message,
        ))
        return ResultBean<String>(code, message, null).okJsonResponse()
    }
}
