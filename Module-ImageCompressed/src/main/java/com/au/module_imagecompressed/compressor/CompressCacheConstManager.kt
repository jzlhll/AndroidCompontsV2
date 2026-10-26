package com.au.module_imagecompressed.compressor

import android.net.Uri
import com.au.module_android.Globals
import com.au.module_android.log.logEx
import com.au.module_android.log.logdNoFile
import com.au.module_android.utilsmedia.isFileScheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random
import kotlin.time.Duration.Companion.days

/** 管理图片处理临时文件，提供上传启动前的进程级过期清理。 */
object CompressCacheConstManager {
    private class CleanupStats {
        var files = 0
        var bytes = 0L
        var expired = 0
        var deleted = 0
        var deletedBytes = 0L
        var failed = 0
    }

    private val cleanupMutex = Mutex()
    private var expiredCleanupCompleted = false

    /** 压缩、拷贝及裁剪共用的临时目录，访问目录不会触发过期清理。 */
    val cacheDir: File by lazy {
        File(Globals.goodCacheDir, "imageCompress").apply {
            check(mkdirs() || isDirectory) { "Cannot create image compression cache: $absolutePath" }
        }
    }

    /** 在上传任务首次开始或恢复前调用并等待完成，每个进程只清理一次五天前的文件。 */
    suspend fun cleanupExpiredOnce() = withContext(Dispatchers.IO) {
        cleanupMutex.withLock {
            if (expiredCleanupCompleted) return@withLock
            currentCoroutineContext().ensureActive()
            val expiredBefore = System.currentTimeMillis() - 5.days.inWholeMilliseconds
            val finished = CleanupStats()
            val temporary = CleanupStats()
            cacheDir.listFiles()?.forEach { file ->
                currentCoroutineContext().ensureActive()
                if (!file.isFile) return@forEach
                val stats = if (file.name.endsWith(".part") || file.name.endsWith(".tmp")) temporary else finished
                val size = file.length()
                stats.files++
                stats.bytes += size
                if (file.lastModified() < expiredBefore) {
                    stats.expired++
                    if (file.delete()) {
                        stats.deleted++
                        stats.deletedBytes += size
                    } else {
                        stats.failed++
                        logdNoFile("ImageProcess") {
                            "cache cleanup: failed to delete ${file.absolutePath}"
                        }
                    }
                }
            }
            listOf("finished" to finished, "temporary" to temporary).forEach { (type, stats) ->
                logdNoFile("ImageProcess") {
                    "cache cleanup: type=$type, files=${stats.files}, bytes=${stats.bytes}, " +
                        "expired=${stats.expired}, deleted=${stats.deleted}, " +
                        "deletedBytes=${stats.deletedBytes}, failed=${stats.failed}"
                }
            }
            expiredCleanupCompleted = true
        }
    }

    /** 按本目录文件名删除临时文件，删除结果与 File 版本一致。 */
    fun delete(fileName: String): Boolean {
        return delete(File(cacheDir, fileName))
    }

    /**
     * 仅删除直属于本缓存目录的临时文件，供使用完成后清理。
     *
     * @return 本目录文件已删除或已不存在时返回 true，目录不匹配或删除失败时返回 false。
     */
    fun delete(file: File): Boolean {
        return try {
            val target = file.canonicalFile
            target.parentFile == cacheDir.canonicalFile && (!target.exists() || (target.isFile && target.delete()))
        } catch (e: Exception) {
            logEx("ImageProcess", throwable = e) {
                "cache delete failed: ${file.absolutePath}"
            }
            false
        }
    }

    /** 仅接受 file scheme 的 Uri，转换为 File 后复用本目录检查和删除逻辑。 */
    fun delete(uri: Uri): Boolean {
        if (!isFileScheme(uri)) return false
        val path = uri.path ?: return false
        return delete(File(path))
    }

    /** 调用方保证临时文件已完整关闭、路径位于同一缓存目录；改名成功后才公开成品。 */
    fun publish(temporary: File, output: File) {
        check(temporary.renameTo(output)) {
            "Cannot publish compression output: ${output.absolutePath}"
        }
    }

    /** 分配带目标扩展名的压缩输出路径。 */
    fun createCompressOutputFile(extension: String = "jpg"): File {
        val timestamp = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        do {
            val randomSuffix = Random.nextLong(1_000_000, 9_999_999)
            val fileName = "compress${timestamp}_${randomSuffix}.$extension"
            val file = File(cacheDir, fileName)
            if (!file.exists() && !File(cacheDir, "$fileName.part").exists()) return file
        } while(true)
    }

    /** 分配拷贝输出路径，保留原文件名并避开已有文件。 */
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
