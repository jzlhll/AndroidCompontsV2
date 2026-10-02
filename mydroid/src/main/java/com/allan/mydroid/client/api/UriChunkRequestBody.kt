package com.allan.mydroid.client.api

import android.content.ContentResolver
import android.net.Uri
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink

/**
 * 从 ContentResolver 流式读取 Uri 指定 [offset]/[length] 范围字节作为 multipart 请求体。
 * 不一次性载入内存，避免大文件 OOM。
 */
class UriChunkRequestBody(
    private val resolver: ContentResolver,
    private val uri: Uri,
    private val offset: Long,
    private val length: Long,
    private val checkActive: () -> Unit = {}
) : RequestBody() {
    override fun contentType(): MediaType? = null

    override fun contentLength(): Long = length

    override fun writeTo(sink: BufferedSink) {
        try {
            checkActive()
            resolver.openInputStream(uri).use { input ->
                if (input == null) {
                    throw java.io.IOException("Cannot open selected file")
                }
                var skipped = 0L
                while (skipped < offset) {
                    checkActive()
                    val s = input.skip(offset - skipped)
                    if (s > 0) {
                        skipped += s
                    } else {
                        if (input.read() == -1) throw java.io.EOFException("Cannot seek to $offset")
                        skipped++
                    }
                }
                var remaining = length
                val buf = ByteArray(8 * 1024)
                while (remaining > 0) {
                    checkActive()
                    val toRead = if (remaining < buf.size) remaining.toInt() else buf.size
                    val read = input.read(buf, 0, toRead)
                    if (read == -1) throw java.io.EOFException("Missing $remaining bytes")
                    if (read == 0) continue
                    sink.write(buf, 0, read)
                    remaining -= read
                }
            }
        } catch (e: java.io.IOException) {
            throw e
        } catch (e: Exception) {
            throw java.io.IOException("Cannot read upload chunk", e)
        }
    }
}
