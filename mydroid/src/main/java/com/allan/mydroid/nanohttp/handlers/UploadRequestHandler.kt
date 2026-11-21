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
                UPLOAD_CHUNK -> handleUploadChunk(session)
                MERGE_CHUNKS -> handleMergeChunk(session)
                else -> {
                    chunkUploadHandler.handleAbortChunk(session)
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
                emitProgress(result.key, ReceivingFileInfo(
                    chunk.fileName, "", chunk.chunkIndex, chunk.totalChunks, PROCESS_CHUNK
                ))
                ResultBean(CODE_SUC, "Chunk received", chunk).okJsonResponse()
            }
            is ChunkUploadHandler.ChunkResult.Failed -> {
                val error = result.error
                logEx(throwable = error) { "Receive chunk failed" }
                emitProgress(result.key, ReceivingFileInfo(
                    result.fileName, "", result.chunkIndex, result.totalChunks, PROCESS_CHUNK_ERROR,
                    Globals.getString(R.string.chunk_receiver_error)
                ))
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
        val request = chunkUploadHandler.readMergeRequest(session) ?: return ResultBean<String>(
            CODE_FAIL, Globals.getString(R.string.error_merge_chunk_params), null
        ).badRequestJsonResponse()
        emitProgress(request.key, ReceivingFileInfo(
            request.fileName, "", request.totalChunks, request.totalChunks, PROCESS_MERGING
        ))
        return when (val result = chunkUploadHandler.handleMergeChunk(request)) {
            is ChunkUploadHandler.MergeResult.Merged -> publish(request, result)
            is ChunkUploadHandler.MergeResult.Failed -> mergeFailure(request, result.code, result.message, result.error)
        }
    }

    private fun publish(request: ChunkUploadHandler.MergeRequest, result: ChunkUploadHandler.MergeResult.Merged): Response {
        val temp = result.temporaryFile
        val md5 = result.md5
        return try {
            val outputFile = if (apkSession != null) {
                apkSession.publish(temp, request.fileName)
            } else {
                TransferFiles.publish(temp, request.fileName).also { file ->
                    runBlocking { fileListRepository.addReceivedFile(file, md5) }
                    receiverFlowsObj.emitFileMerged(file)
                }
            }
            emitProgress(request.key, ReceivingFileInfo(
                request.fileName, md5, request.totalChunks, request.totalChunks, PROCESS_COMPLETED
            ))
            ResultBean(CODE_SUC, Globals.getString(R.string.file_merge_success),
                mapOf("fileName" to outputFile.name, "md5" to md5)).okJsonResponse()
        } catch (e: Exception) {
            mergeFailure(request, CODE_FAIL_MERGE_CHUNK, e.message, e)
        } finally {
            temp.delete()
        }
    }

    private fun mergeFailure(request: ChunkUploadHandler.MergeRequest, code: String, message: String?, error: Exception?): Response {
        if (error != null) logEx(throwable = error) { "Merge chunks failed" }
        emitProgress(request.key, ReceivingFileInfo(
            request.fileName, "", request.totalChunks, request.totalChunks, PROCESS_MERGE_ERROR, message
        ))
        return ResultBean<String>(code, message, null).okJsonResponse()
    }

    private fun emitProgress(key: String, info: ReceivingFileInfo) {
        if (apkSession == null) receiverFlowsObj.emitProgress(mapOf(key to info))
    }
}
