package com.au.module_imagecompressed.compressor

import com.au.module_android.utilsfile.SimpleFilesLruCache
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

object CompressCacheConstManager {
    const val CACHE_DIR_NAME = "au_compressor"
    val mgr = SimpleFilesLruCache(CACHE_DIR_NAME, maxSize = 250 * 1024 * 1024)

    val cacheDir: File
        get() = mgr.cacheDir

    fun createCompressOutputFile(): File {
        val timestamp = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        do {
            val randomSuffix = Random.nextLong(1_000_000, 9_999_999)
            val fileName = "compress${timestamp}_${randomSuffix}.jpg"
            val file = File(cacheDir, fileName)
            if (!file.exists()) return file
        } while(true)
    }

    fun createCopyOutputFile(origName: String = "", extension: String = "jpg"): File {
        if (origName.isEmpty()) {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val randomSuffix = Random.nextInt(100_000, 1_000_000)
            val fileName = "copy${timestamp}_${randomSuffix}.$extension"
            return File(cacheDir, fileName)
        }

        var fileName = "$origName.$extension"
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