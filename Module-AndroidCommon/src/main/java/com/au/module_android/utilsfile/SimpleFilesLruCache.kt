package com.au.module_android.utilsfile

import com.au.module_android.Globals
import com.au.module_android.log.logdNoFile
import com.au.module_android.utilthread.SingleCoroutineTaskExecutor
import kotlinx.coroutines.delay
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

/**
 * 文件管理类，记录文件访问信息而不实际管理文件存储
 * 支持嵌套目录结构
 */
class SimpleFilesLruCache(
    val dirName: String,
    private val maxSize: Long = 100 * 1024 * 1024, // 默认100MB
    private val clearToRatio: Double = 0.6,
    isFileOrCacheParent : Boolean = false
) {
    private var singleScope: SingleCoroutineTaskExecutor? = null
    private fun getOrCreateScope() : SingleCoroutineTaskExecutor {
        if (singleScope != null) return singleScope!!
        val scope = SingleCoroutineTaskExecutor("file_lru_cache")
        singleScope = scope
        return scope
    }

    val cacheDir = File(if(isFileOrCacheParent) Globals.goodFilesDir else Globals.goodCacheDir, dirName)

    // 记录文件的访问时间和大小，key就是全路径
    private val fileMetadata = ConcurrentHashMap<String, FileMetadata>()

    // 文件元数据类
    private data class FileMetadata(
        var accessTime: Long,
        var fileSize: Long,
    )

    // 文件操作类型枚举
    enum class FileOperateType {
        SAVE,     // 保存文件
        READ,     // 读取文件
        DELETE    // 删除文件
    }

    init {
        // 初始化时扫描目录，构建文件信息map
        scanDirectory()
    }

    fun shutdown() {
        val scope = singleScope
        singleScope = null
        scope?.shutdown()
    }

    /**
     * 递归扫描目录
     * @param currentDir 当前扫描的目录
     */
    fun scanDirectoryRecursive(currentDir: File) {
        val files = currentDir.listFiles() ?: return
        for (file in files) {
            if (file.isFile) {
                val metadata = FileMetadata(
                    accessTime = file.lastModified(),
                    fileSize = file.length(),
                )
                fileMetadata[file.absolutePath] = metadata
            } else if (file.isDirectory) {
                // 递归扫描子目录
                scanDirectoryRecursive(file)
            }
        }
    }

    /**
     * 初始化时扫描目录，构建文件信息map
     * 递归扫描所有子目录
     * 也可以用来做清理作用
     */
    fun scanDirectory(shutdownWhenOver: Boolean = false) {
        getOrCreateScope().submit {
            fileMetadata.clear()
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
                return@submit
            }

            scanDirectoryRecursive(cacheDir)
            // 检查是否需要清理旧文件
            val size = getTotalSize()
            if (size > maxSize) {
                logdNoFile { "cache total size: $size max:$maxSize" }
                cleanupOldFiles(size)
            }

            if (shutdownWhenOver) {
                shutdown()
            }
        }
    }

    /**
     * 文件操作后的回调，记录文件信息
     * @param file 文件对象
     * @param operateType 操作类型
     */
    fun afterFileOperator(file: File, operateType: FileOperateType) {
        getOrCreateScope().submit {
            // 计算相对于cacheDir的路径
            when (operateType) {
                FileOperateType.SAVE -> {
                    // SAVE操作需要文件存在，并记录文件大小
                    if (file.exists()) {
                        val fileTime = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java).lastAccessTime().toMillis()
                        fileMetadata[file.absolutePath] = FileMetadata(
                            accessTime = fileTime,
                            fileSize = file.length(),
                        )

                        // 检查是否需要清理旧文件
                        val total = getTotalSize()
                        if (total > maxSize) {
                          //  logdNoFile { "$operateType ${file.path} , time: $fileTime size:${file.length()} need $total / $maxSize" }
                            cleanupOldFiles(total)
                        } else {
                          //  logdNoFile { "$operateType ${file.path} , time: $fileTime size:${file.length()} noNeed $total / $maxSize" }
                        }
                    }
                }

                FileOperateType.READ -> {
                    // READ操作更新访问时间
                    if (file.exists()) {
                        fileMetadata[file.absolutePath]?.let { metadata ->
                            metadata.accessTime = System.currentTimeMillis()
                        }
                        // 设置为当前时间
                        val newAccessTime = FileTime.fromMillis(Date().time)
                        Files.setAttribute(file.toPath(), "lastAccessTime", newAccessTime)
                        logdNoFile { "$operateType ${file.path} , time:$newAccessTime" }
                    } else {
                        // 文件不存在，从记录中移除
                        fileMetadata.remove(file.absolutePath)
                    }
                }

                FileOperateType.DELETE -> {
                    // DELETE操作从记录中移除
                    fileMetadata.remove(file.absolutePath)
                }
            }
        }
    }

    /**
     * 清理旧文件（简单的LRU实现）
     */
    private suspend fun cleanupOldFiles(totalSize: Long) {
        delay(0)
        // 按访问时间排序
        val sortedEntries = fileMetadata.entries
            .sortedBy { it.value.accessTime }
            .toList()

        var currentSize = totalSize
        val clearToSize = maxSize * clearToRatio

        logdNoFile { "deleted totalSize $currentSize , clearToSize: $clearToSize" }
        // 删除最旧的，直到满足大小限制
        for (entry in sortedEntries) {
            if (currentSize <= clearToSize) {
                break // 删除到指定比例就停止
            }

            val file = File(entry.key)
            if (file.exists()) {
                val deleted = file.delete()
                logdNoFile { "deleted ${entry.key} , size: ${entry.value.fileSize}" }
                if (deleted) {
                    currentSize -= entry.value.fileSize
                    fileMetadata.remove(entry.key)
                }
            } else {
                // 文件不存在，直接从map中移除
                fileMetadata.remove(entry.key)
                currentSize -= entry.value.fileSize
            }
        }
    }

    /**
     * 计算总大小
     */
    fun getTotalSize(): Long {
        return fileMetadata.values.sumOf { it.fileSize }
    }

    /**
     * 检查文件是否存在
     */
    fun fileExists(fileName:String): Boolean {
        return File(cacheDir, fileName).exists()
    }

    /**
     * 检查文件是否存在
     */
    fun fileExists(fileName:String, vararg dirs:String): Boolean {
        val relativePath = dirs.joinToString(File.separator) + File.separator + fileName
        val file = File(cacheDir, relativePath)
        return file.exists()
    }

    /**
     * 根据相对路径获取文件
     */
    fun getFile(fileName:String): File? {
        val file = File(cacheDir, fileName)
        return if (file.exists()) file else null
    }

    /**
     * 根据相对路径获取文件
     */
    fun getFile(fileName:String, vararg dirs:String): File? {
        val relativePath = dirs.joinToString(File.separator) + File.separator + fileName
        val file = File(cacheDir, relativePath)
        return if (file.exists()) file else null
    }

    /**
     * 根据文件名（不包含路径）获取文件列表
     * 如果有重名文件，会返回所有匹配的文件
     */
    fun getFilesByName(fileName: String): List<File> {
        return fileMetadata.keys
            .filter { it.endsWith(fileName) }
            .map { str-> File(str) }
            .filter { it.exists() }
    }

    /**
     * 清理所有文件和记录。常用于app的设置中调用清理干净。
     */
    fun clearAll() {
        getOrCreateScope().submit {
            fileMetadata.clear()
            val files = cacheDir.listFiles()
            if (files != null) {
                for (file in files) {
                    if(file.isFile) file.delete()
                }
            }
        }
    }
}