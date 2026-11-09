package com.au.module_ktor_android.transfer

import android.content.ContentResolver
import android.net.Uri
import com.au.module_android.utilsmedia.myParse
import com.au.module_android.utilsmedia.length
import com.au.module_ktor_base.transfer.UploadSource
import com.au.module_ktor_base.transfer.storageOperation
import io.ktor.http.ContentType
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

/** replayable 由调用方保证内容稳定；重放时同时核对文件长度和修改时间。 */
class FileUploadSource(
    private val file: File,
    override val contentType: ContentType = ContentType.Application.OctetStream,
    override val contentLength: Long? = null,
    override val replayable: Boolean = false,
) : UploadSource {
    private val mutex = Mutex()
    private var snapshot: Pair<Long, Long>? = null

    init { require(contentLength == null || contentLength >= 0) }

    override suspend fun open(): ByteReadChannel {
        val ownerContext = currentCoroutineContext()
        return withContext(Dispatchers.IO) { storageOperation {
            mutex.withLock {
                if (!file.isFile) throw FileNotFoundException("Upload file is unavailable")
                val current = file.length() to file.lastModified()
                if (contentLength != null && contentLength != current.first || snapshot != null && snapshot != current) {
                    throw IllegalStateException("Upload file changed")
                }
                snapshot = current
                file.inputStream().toByteReadChannel(ownerContext + Dispatchers.IO)
            }
        } }
    }
}

/** Uri 默认不重放，来源为空或权限错误明确作为存储错误返回。 */
class UriUploadSource(
    private val resolver: ContentResolver,
    private val uri: Uri,
    override val contentType: ContentType = ContentType.Application.OctetStream,
    override val contentLength: Long? = null,
    override val replayable: Boolean = false,
) : UploadSource {
    init { require(contentLength == null || contentLength >= 0) }

    override suspend fun open(): ByteReadChannel {
        val ownerContext = currentCoroutineContext()
        return withContext(Dispatchers.IO) { storageOperation {
            val stream = resolver.openInputStream(uri) ?: throw FileNotFoundException("Uri returned no input stream")
            stream.toByteReadChannel(ownerContext + Dispatchers.IO)
        } }
    }
}

suspend fun uriUploadSource(resolver: ContentResolver, uri: Uri, replayable: Boolean = false): UriUploadSource =
    withContext(Dispatchers.IO) {
        storageOperation {
            val info = uri.myParse(resolver)
            UriUploadSource(
                resolver, uri,
                info.mimeType.takeIf { it.isNotBlank() }?.let(ContentType::parse) ?: ContentType.Application.OctetStream,
                uri.length(resolver).takeIf { it >= 0 }, replayable,
            )
        }
    }
