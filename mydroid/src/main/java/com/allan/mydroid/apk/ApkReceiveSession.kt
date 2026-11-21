package com.allan.mydroid.apk

import com.allan.mydroid.R
import com.allan.mydroid.repository.TransferFiles
import com.au.module_android.Globals
import com.au.module_android.log.logd
import com.au.module_android.log.loge
import com.au.module_android.utils.launchOnIOThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

/** 管理当前 HTTP 服务的 APK 缓存，服务关闭后拒绝发布迟到的合并结果。 */
class ApkReceiveSession {
    private val tempDir = File(cacheRoot, "tmp/${UUID.randomUUID()}")
    private var closed = false

    val requestDir = File(tempDir, "request")
    val chunksDir = File(tempDir, "chunks")
    val stagingDir = File(tempDir, "merge")

    @Suppress("DEPRECATION")
    fun publish(temp: File, name: String): File {
        require(Globals.app.packageManager.getPackageArchiveInfo(temp.absolutePath, 0) != null) {
            Globals.getString(R.string.mydroid_apk_invalid)
        }
        synchronized(this) {
            check(!closed) { "APK receive session is closed" }
            val target = TransferFiles.publish(temp, name, File(cacheRoot, "received"))
            cachedFilesFlow.update { files -> listOf(target) + files }
            return target
        }
    }

    @Synchronized
    fun close() {
        closed = true
    }

    fun clearTemporaryFiles() {
        Globals.mainScope.launchOnIOThread {
            if (!tempDir.deleteRecursively()) loge { "Cannot remove APK receive temporary directory" }
        }
    }

    companion object {
        private val cachedFilesFlow = MutableStateFlow<List<File>>(emptyList())
        val filesFlow = cachedFilesFlow.asStateFlow()

        // 只清理进入本页前遗留的数据，各次打开保留本进程已收完的 APK。
        private val cacheRoot: File by lazy {
            File(Globals.app.cacheDir, "shared/apkReceive").apply {
                val oldFiles = File(this, "received").walkTopDown().count(File::isFile)
                val oldTempFiles = File(this, "tmp").walkTopDown().count(File::isFile)
                logd { "Clear APK receive cache: $oldFiles completed files, $oldTempFiles temporary files" }
                if (!deleteRecursively() || !mkdirs()) throw IOException("Cannot prepare APK receive cache")
            }
        }

        suspend fun prepareCache() {
            withContext(Dispatchers.IO) { cacheRoot }
        }
    }
}
