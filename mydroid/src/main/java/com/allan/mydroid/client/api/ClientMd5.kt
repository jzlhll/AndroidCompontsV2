package com.allan.mydroid.client.api

import android.net.Uri
import com.au.module_android.Globals
import java.io.InputStream
import java.security.MessageDigest

/**
 * client 端流式 MD5 工具。结果与 JS spark-md5 一致（十六进制小写）。
 * Android 端用协程子线程流式读取，不需要 Worker。
 */
object ClientMd5 {
    private const val BUFFER_SIZE = 8 * 1024

    fun streamMd5(inputStream: InputStream): String {
        val digest = MessageDigest.getInstance("MD5")
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val read = inputStream.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun streamMd5(uri: Uri): String {
        return Globals.app.contentResolver.openInputStream(uri).use { stream ->
            streamMd5(stream ?: throw IllegalStateException("openInputStream null: $uri"))
        }
    }
}
