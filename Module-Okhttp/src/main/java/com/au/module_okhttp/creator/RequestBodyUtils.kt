package com.au.module_okhttp.creator

import android.content.Context
import android.net.Uri
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source

/**
 * 兼容本地File和远程Uri的转变成okhttp的RequestBody
 */
fun Uri.asInputStreamRequestBody(context:Context, length:Long, contentType: MediaType? = null) : RequestBody {
    return object : RequestBody() {
        override fun contentType(): MediaType? {
            return contentType
        }

        override fun contentLength(): Long {
            return length
        }

        override fun writeTo(sink: BufferedSink) {
            context.contentResolver.openInputStream(this@asInputStreamRequestBody)?.run {
                source().use {
                    sink.writeAll(it)
                }
            }
        }
    }
}

fun Uri.asInputStreamRequestBody(context:Context, length:Long, mediaType: String)
        = asInputStreamRequestBody(context, length, mediaType.toMediaTypeOrNull())