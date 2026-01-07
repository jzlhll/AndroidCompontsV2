package com.au.module_android.utilsfile

import com.au.module_android.Globals
import com.au.module_android.log.logd
import com.au.module_android.utils.withIOThread
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
    dirName: String,
    private val maxSize: Long = 100 * 1024 * 1024, // 默认100MB
    private val clearToRatio: Float = 0.6f
) {
    val cacheDir = File(Globals.goodCacheDir, dirName)

    // 记录文件的访问时间和大小，key为absolutePath
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

    /**
     * 初始化时扫描目录，构建文件信息map
     * 递归扫描所有子目录
     */
    fun scanDirectory() {
        fileMetadata.clear()

        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
            return
        }

        scanDirectoryRecursive(cacheDir)

        // 检查是否需要清理旧文件
        if (getTotalSize() > maxSize) {
            cleanupOldFiles()
        }

        deleteEmptyDirectoriesInCacheDir()
    }

    /**
     * 递归扫描目录
     * @param currentDir 当前扫描的目录
     * @param relativePath 相对于cacheDir的路径
     */
    private fun scanDirectoryRecursive(currentDir: File) {
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
     * 文件操作后的回调，记录文件信息
     * @param file 文件对象
     * @param operateType 操作类型
     */
    fun afterFileOperator(file: File, operateType: FileOperateType) {
        // 计算相对于cacheDir的路径
        when (operateType) {
            FileOperateType.SAVE -> {
                // SAVE操作需要文件存在，并记录文件大小
                if (file.exists()) {
                    val fileTime = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java).lastAccessTime().toMillis()
                    logd { "$operateType ${file.absolutePath} $fileTime" }
                    fileMetadata[file.absolutePath] = FileMetadata(
                        accessTime = fileTime,
                        fileSize = file.length(),
                    )

                    // 检查是否需要清理旧文件
                    if (getTotalSize() > maxSize) {
                        cleanupOldFiles()
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
                    logd { "$operateType ${file.absolutePath} $newAccessTime" }
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

    /**
     * 清理旧文件（简单的LRU实现）
     */
    private fun cleanupOldFiles() {
        // 按访问时间排序
        val sortedEntries = fileMetadata.entries
            .sortedBy { it.value.accessTime }
            .toList()

        var currentSize = getTotalSize()
        val clearToSize = maxSize * clearToRatio

        // 删除最旧的，直到满足大小限制
        for (entry in sortedEntries) {
            if (currentSize <= clearToSize) {
                break // 删除到指定比例就停止
            }

            val file = File(cacheDir, entry.key)
            if (file.exists()) {
                val deleted = file.delete()
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
     * @param relativePath 文件的相对路径
     */
    fun fileExists(fileName:String, vararg dirs:String): Boolean {
        val relativePath = dirs.joinToString(File.separator) + File.separator + fileName
        val file = File(cacheDir, relativePath)
        return file.exists()
    }

    /**
     * 根据相对路径获取文件
     * @param relativePath 文件的相对路径
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
            .map { File(cacheDir, it) }
            .filter { it.exists() }
    }

    /**
     * 清理所有文件和记录。常用于app的设置中调用清理干净。
     */
    suspend fun clearAll() {
        withIOThread {
            fileMetadata.keys.forEach { relativePath ->
                val file = File(cacheDir, relativePath)
                if (file.exists()) {
                    file.delete()
                }
            }
            fileMetadata.clear()

            // 清理cacheDir下的所有空子目录
            deleteEmptyDirectoriesInCacheDir()
        }
    }

    /**
     * 把子目录和更深的子目录判断为空删除
     */
    private fun deleteEmptyDirectoriesInCacheDir() {
        val files = cacheDir.listFiles()
        if (files != null) {
            for (file in files) {
                if (file.isDirectory) {
                    deleteEmptyDirectoriesRecursive(file)
                }
            }
        }
    }

    /**
     * 递归删除目录（如果目录为空）
     */
    private fun deleteEmptyDirectoriesRecursive(directory: File) {
        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                deleteEmptyDirectoriesRecursive(file)
            }
        }

        // 检查目录是否为空
        val files = directory.listFiles()
        if (files == null || files.isEmpty()) {
            directory.delete()
        }
    }

    /**
     * 手动触发清理
     */
    fun manualCleanupOlds() {
        if (isNeedCleanupOlds()) {
            cleanupOldFiles()
        }
    }

    /**
     * 检查是否需要清理（超过最大大小）
     */
    fun isNeedCleanupOlds(): Boolean {
        return getTotalSize() > maxSize
    }
}