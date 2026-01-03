package com.au.module_android.utils

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import com.au.module_android.Globals
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * 删除文件夹
 */
fun File?.deleteFileDir(): Boolean {
    this ?: return false
    return if (this.isDirectory && this.exists()) {
        this.listFiles()?.forEach {
            if (it.isFile) {
                it.delete()
            } else {
                it.deleteFileDir()
            }
        }
        this.delete()
    } else {
        false
    }
}

/**
 * 删除文件或者文件夹
 */
fun File?.deleteAll(): Boolean {
    this ?: return false
    return when {
        isFile -> this.delete()
        isDirectory -> deleteFileDir()
        else -> false
    }
}

fun File?.getDirSize(): Long {
    this ?: return 0
    if (this.isFile) {
        return this.length()
    }
    var size = 0L
    if (this.isDirectory) {
        this.listFiles()?.forEach {
            size += it.getDirSize()
        }
    }
    return size
}

/**
 * 清理文件夹下旧文件
 * @param dirPath 文件夹路径
 * @param deltaTs 默认15天
 */
fun clearDirOldFiles(dirPath:String, deltaTs:Long = 15L * 3600 * 24 * 1000) {
    var count = 0
    do {
        val file = File(dirPath)
        if (!file.exists()) {
            break
        }
        val files = file.listFiles() ?: break
        for (f in files) {
            if (f.exists()) {
                try {
                    val time = f.lastModified()
                    if (System.currentTimeMillis() - time > deltaTs) { //N天前的旧文件删除。
                        if (f.delete()) {
                            count++
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    } while (false)
}

/**
 * Create a file if it doesn't exist, otherwise delete old file before creating.
 *
 * @param file The file.
 * @return `true`: success<br></br>`false`: fail
 */
fun createFileByDeleteOldFile(file: File?): Boolean {
    if (file == null) return false
    // file exists and unsuccessfully delete then return false
    if (file.exists() && !file.delete()) return false
    if (!createOrExistsDir(file.getParentFile())) return false
    try {
        return file.createNewFile()
    } catch (e: IOException) {
        e.printStackTrace()
        return false
    }
}

fun createFileIfNotExist(file: File): File? {
    if (file.isDirectory && !file.exists()) {
        val r = file.mkdirs()
        return if (r) file else null
    } else if (file.isFile && !file.exists()) {
        val r = ignoreError {
            file.createNewFile()
        }
        return if (r == true) file else null
    }
    return null
}

/**
 * Create a directory if it doesn't exist, otherwise do nothing.
 *
 * @param file The file.
 * @return `true`: exists or creates successfully<br></br>`false`: otherwise
 */
fun createOrExistsDir(file: File?): Boolean {
    return file != null && (if (file.exists()) file.isDirectory() else file.mkdirs())
}

/**
 * Return whether the file exists.
 *
 * Android 10 (API 29) 及以上版本的存储变更
 * Android 10 引入了分区存储 (Scoped Storage)，应用对文件系统的访问受到限制
 * 传统的 file.exists() 在某些情况下可能无法正确判断文件是否存在
 * 外部文件访问机制
 * openAssetFileDescriptor 可以通过 ContentResolver 访问其他应用或系统提供的文件
 * 适用于 URI 格式的文件路径，而不仅仅是本地文件系统路径
 *
 * @return `true`: yes<br></br>`false`: no
 */
fun File?.existsCompat() : Boolean {
    if (this == null) return false
    return if (this.exists()) {
        true
    } else isFileExists(this.absolutePath)
}

fun getFileByPath(filePath: String?): File? {
    return if (isSpace(filePath)) null else File(filePath)
}

/**
 * Return whether the file exists.
 *
 * @param filePath The path of file.
 * @return `true`: yes<br></br>`false`: no
 */
fun isFileExists(filePath: String): Boolean {
    val file: File = getFileByPath(filePath) ?: return false
    return if (file.exists()) {
        true
    } else isFileExistsApi29(filePath)
}

private fun isFileExistsApi29(filePath: String): Boolean {
    if (Build.VERSION.SDK_INT >= 29) {
        try {
            val uri = filePath.toUri()
            val cr: ContentResolver = Globals.app.contentResolver
            val afd = cr.openAssetFileDescriptor(uri, "r") ?: return false
            ignoreError { afd.close() }
        } catch (_: FileNotFoundException) {
            return false
        }
        return true
    }
    return false
}

/**
 * Notify system to scan the file.
 *
 * @param file The file.
 */
fun notifySystemToScan(file: File?) {
    if (file == null || !file.exists()) return
    val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
    intent.setData(Uri.parse("file://" + file.getAbsolutePath()))
    Globals.app.sendBroadcast(intent)
}

fun deleteIfExist(file: File?): Boolean {
    return if (file != null && file.exists()) {
        file.delete()
    } else false
}

fun copyFile(oldFile: File, destFile: File): String? {
    var bis: BufferedInputStream? = null
    var bos: BufferedOutputStream? = null
    try {
        if (!oldFile.exists()) {
            return null
        } else if (!oldFile.isFile) {
            return null
        } else if (!oldFile.canRead()) {
            return null
        }
        deleteIfExist(destFile)
        createFileIfNotExist(destFile)
        val fileInputStream = FileInputStream(oldFile)
        bis = BufferedInputStream(fileInputStream)
        val fileOutputStream = FileOutputStream(destFile)
        bos = BufferedOutputStream(fileOutputStream)
        val buffer = ByteArray(8192)
        var byteRead: Int
        while (-1 != bis.read(buffer).also { byteRead = it }) {
            bos.write(buffer, 0, byteRead)
        }
        bos.flush()
        return destFile.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        try {
            bis?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            bos?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    return null
}

fun copyFile(oldFile: File, destDir: String?, fileName: String?): String? {
    return try {
        val destFile = File(destDir, fileName)
        createFileByDeleteOldFile(destFile)
        copyFile(oldFile, destFile)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun copyFile(oldFile: String, destFileStr: String): String? {
    ignoreError {
        val destFile = File(destFileStr)
        createFileByDeleteOldFile(destFile)
        return copyFile(File(oldFile), destFile)
    }
    return null
}

@Throws(IOException::class)
fun copyFile(
    inputStream: InputStream,
    out: OutputStream
): Long {
    var progress: Long = 0
    val buffer = ByteArray(8192)

    var t: Int
    while ((inputStream.read(buffer).also { t = it }) != -1) {
        out.write(buffer, 0, t)
        progress += t.toLong()
    }
    return progress
}

