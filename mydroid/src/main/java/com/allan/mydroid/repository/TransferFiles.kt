package com.allan.mydroid.repository

import com.allan.mydroid.globals.nanoTempCacheMergedDir
import java.io.File
import java.io.IOException
import java.util.UUID
import com.au.module_android.Globals
import com.au.module_android.log.loge

/** 上传合并与客户端下载共用的落盘入口，校验完成后发布，串行选择名称以避免覆盖。 */
object TransferFiles {
    // 首次清理与创建共享 lazy 锁；启动清理晚于下载启动时也不会误删本进程的文件。
    private val stagingDir: File by lazy {
        val root = File(File(nanoTempCacheMergedDir()).parentFile, "nanoStaging")
        root.listFiles()?.forEach { file ->
            if (!file.deleteRecursively()) loge { "Cannot remove abandoned transfer: ${file.name}" }
        }
        // 兼容此前放在 cacheDir 根目录的上传准备文件。
        Globals.app.cacheDir.listFiles()?.filter { it.name.startsWith("upload-") && it.name.endsWith(".tmp") }
            ?.forEach { file ->
                if (!file.delete()) loge { "Cannot remove abandoned upload: ${file.name}" }
            }
        File(root, UUID.randomUUID().toString()).apply {
            if (!isDirectory && !mkdirs()) throw IOException("Cannot create transfer directory")
        }
    }

    fun clearAbandonedFiles() {
        stagingDir
    }

    fun temporaryFile(): File = File.createTempFile("transfer-", ".part", stagingDir)

    @Synchronized
    fun publish(temp: File, rawName: String): File {
        val dir = File(nanoTempCacheMergedDir())
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Cannot create destination directory")
        val name = rawName.replace('\\', '/').substringAfterLast('/').trim()
        require(name.isNotBlank() && name != "." && name != "..") { "Invalid file name" }
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var target = File(dir, name)
        var index = 1
        while (target.exists()) {
            target = File(dir, "$stem($index)$ext")
            index++
        }
        if (!temp.renameTo(target)) throw IOException("Cannot publish received file")
        return target
    }
}
