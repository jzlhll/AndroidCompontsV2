package com.au.module_imagecompressed.compressor

import com.au.module_android.utilsfile.SimpleFilesLruCache
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

object CompressCacheConstManager {
    const val CACHE_DIR_NAME = "au_compressor"
    val mgr = SimpleFilesLruCache(CACHE_DIR_NAME, maxSize = 200_000_000)

    val cacheDir: File
        get() = mgr.cacheDir

    fun createCompressOutputFile(): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val randomSuffix = Random.nextInt(100_000, 1_000_000)
        val fileName = "compress_${timestamp}_${randomSuffix}.jpg"
        return File(cacheDir, fileName)
    }

    fun createCopyOutputFile(origName: String = "", extension: String = "jpg"): File {
        if (origName.isEmpty()) {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val randomSuffix = Random.nextInt(100_000, 1_000_000)
            val fileName = "copy_${timestamp}_${randomSuffix}.$extension"
            return File(cacheDir, fileName)
        }

        var fileName = "copy_$origName.$extension"
        var file = File(cacheDir, fileName)
        if (!file.exists()) return file

        var index = 1
        while (true) {
            fileName = "${origName}_$index.$extension"
            file = File(cacheDir, fileName)
            if (!file.exists()) return file
            index++
        }
    }
}