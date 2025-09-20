package com.au.module_imagecompressed

import android.net.Uri
import android.webkit.MimeTypeMap
import com.au.module_android.Globals
import com.au.module_imagecompressed.CropCircleImageFragment.Companion.DIR_CROP
import java.io.File

/**
 * 必须是：
 * 我的cacheDir或者fileDir下的文件来转成UriWrap。
 */
fun File.imageFileConvertToUriWrap(uri: Uri) : UriWrap {
    val extension = extension.lowercase()
    val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
    return UriWrap(uri,
        1,
        this.length(),
        isImage = true,
        beLimitedSize = false,
        beCopied = true,
        mimeType,
        name)
}

private const val SUB_CACHE_DIR = "luban_disk_cache"
private const val COPY_FILE_PREFIX = "copy_"

fun clearLubanAndCropCache(clearLubanCompress:Boolean = true, clearUCrop:Boolean = true) {
    if (clearLubanCompress) {
        try {
            val cmpImagesPath = File(Globals.app.cacheDir.absolutePath + "/$SUB_CACHE_DIR")
            cmpImagesPath.listFiles()?.forEach {
                it.delete()
            }
        } catch (e:Exception) {
            e.printStackTrace()
        }

        try {
            val cmpImagesPath = File(Globals.app.externalCacheDir?.absolutePath + "/$SUB_CACHE_DIR")
            cmpImagesPath.listFiles()?.forEach {
                it.delete()
            }
        } catch (e:Exception) {
            e.printStackTrace()
        }
    }

    if (clearUCrop) {
        try {
            val cmpImagesPath = File(Globals.app.cacheDir.absolutePath + "/$DIR_CROP")
            cmpImagesPath.listFiles()?.forEach {
                it.delete()
            }
        } catch (e:Exception) {
            e.printStackTrace()
        }

        try {
            val cmpImagesPath = File(Globals.app.externalCacheDir?.absolutePath + "/$DIR_CROP")
            cmpImagesPath.listFiles()?.forEach {
                it.delete()
            }
        } catch (e:Exception) {
            e.printStackTrace()
        }
    }
}