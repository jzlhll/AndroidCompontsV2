package com.au.module_android.utilsmedia

import android.net.Uri
import java.io.File

data class UriParsedInfo(
    val uri: Uri,
    val name:String,
    val fileLength:Long,
    val extension:String,
    val mimeType:String = "",
    val fullPath:String? = null, /* 二选一 */
    val relativePath:String? = null, /* 二选一 */
    val videoDuration:Long? = null,
    val isFile: Boolean = false) {
    fun isFullPath() : Boolean = fullPath != null

    fun isUriVideo(): Boolean {
        return mimeType.startsWith("video/")
    }

    fun isUriImage(): Boolean {
        return mimeType.startsWith("image/")
    }

    fun file() : File? {
        val p = fullPath ?: relativePath
        return if (p.isNullOrEmpty()) {
            null
        } else {
            File(p)
        }
    }
}