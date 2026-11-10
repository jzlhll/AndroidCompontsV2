package com.au.module_ktor_base.transfer

import com.au.module_ktor_base.error.StorageException
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonElement

interface UploadSource {
    val contentType: ContentType
    val contentLength: Long?
    val replayable: Boolean
    suspend fun open(): ByteReadChannel
    suspend fun close(channel: ByteReadChannel, cause: Throwable?) { channel.cancel(cause) }
}

interface DownloadSink {
    suspend fun write(buffer: ByteArray, offset: Int, length: Int)
    suspend fun complete()
    suspend fun abort(cause: Throwable)
}

data class MultipartFilePart(val fieldName: String, val fileName: String, val source: UploadSource)

/** 固定的请求体描述，普通正文编码一次复用，文件来源每次尝试重新打开。 */
sealed class KtorBody {
    class Empty : KtorBody()
    class Bytes(bytes: ByteArray, val contentType: ContentType = ContentType.Application.OctetStream) : KtorBody() {
        private val value = bytes.copyOf()
        fun copyBytes(): ByteArray = value.copyOf()
    }
    class Text(val value: String, val contentType: ContentType = ContentType.Text.Plain) : KtorBody()
    class JsonValue(val value: JsonElement) : KtorBody()
    class Form(parameters: Parameters) : KtorBody() {
        val parameters: Parameters = Parameters.build { appendAll(parameters) }
    }
    class Upload(val source: UploadSource) : KtorBody()
    class Multipart(fields: Parameters, files: List<MultipartFilePart>) : KtorBody() {
        val fields: Parameters = Parameters.build { appendAll(fields) }
        val files = files.toList()
    }

    val replayable: Boolean get() = when (this) {
        is Upload -> source.replayable
        is Multipart -> files.all { it.source.replayable }
        else -> true
    }
}

suspend fun <T> storageOperation(block: suspend () -> T): T = try {
    block()
} catch (error: CancellationException) {
    throw error
} catch (error: StorageException) {
    throw error
} catch (error: Exception) {
    throw StorageException(error)
}
