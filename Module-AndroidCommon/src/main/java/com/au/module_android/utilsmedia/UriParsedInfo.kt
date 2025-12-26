package com.au.module_android.utilsmedia

import android.net.Uri
import android.webkit.MimeTypeMap
import com.au.module_android.json.UriSerializer
import kotlinx.serialization.Serializable

@Serializable
data class UriParsedInfo(
    @Serializable(with = UriSerializer::class)
    val uri: Uri,
    val name:String,
    val fileLength:Long,
    val extension:String,
    val mimeType:String = "",
    val fullPath:String? = null, /* 二选一 */
    val relativePath:String? = null, /* 二选一 */
    val videoDuration:Long? = null) {
    fun isFullPath() : Boolean = fullPath != null

    fun isUriHeic() : Boolean{
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        return extension?.lowercase() == "heic"
    }

    fun isUriVideo(): Boolean {
        return mimeType.startsWith("video/")
    }

    fun isUriImage(): Boolean {
        return mimeType.startsWith("image/")
    }
}