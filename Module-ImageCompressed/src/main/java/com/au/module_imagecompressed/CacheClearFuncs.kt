package com.au.module_imagecompressed

import com.au.module_android.Globals
import java.io.File

private const val SUB_CACHE_DIR = "luban_disk_cache"
private const val COPY_FILE_PREFIX = "copy_"

fun clearLubanAndCropCache(clearLubanCompress:Boolean = true) {
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
}