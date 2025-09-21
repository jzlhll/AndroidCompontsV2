package com.au.module_android.utilsmedia

import android.webkit.MimeTypeMap

/**
 * @author allan
 * @date :2024/10/24 15:41
 * @description: file和content的Uri形式的获取。
 */
class MimeUtil(val mimeType: String) {
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