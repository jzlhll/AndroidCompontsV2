package com.allan.mydroid.apk

import com.allan.mydroid.R
import com.allan.mydroid.api.WSApisConst2.PROCESS_ABORTED
import com.allan.mydroid.api.WSApisConst2.PROCESS_CHUNK
import com.allan.mydroid.api.WSApisConst2.PROCESS_CHUNK_ERROR
import com.allan.mydroid.api.WSApisConst2.PROCESS_COMPLETED
import com.allan.mydroid.api.WSApisConst2.PROCESS_MERGE_ERROR
import com.allan.mydroid.api.WSApisConst2.PROCESS_MERGING
import com.allan.mydroid.beansinner.ReceivingFileInfo
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
import java.io.FileInputStream
import java.io.IOException
import java.util.UUID
import kotlin.math.max

/** 管理当前 HTTP 服务的 APK 缓存，服务关闭后拒绝发布迟到的合并结果。 */
class ApkReceiveSession {
    private val tempDir = File(cacheRoot, "tmp/${UUID.randomUUID()}")
    private var closed = false

    val requestDir = File(tempDir, "request")
    val chunksDir = File(tempDir, "chunks")
    val stagingDir = File(tempDir, "merge")

    @Synchronized
    fun changeActiveChunkRequests(delta: Int) {
        if (!closed) activeChunkRequestCount.value += delta
    }

    @Synchronized
    fun startMerging(key: String, info: ReceivingFileInfo): Boolean {
        val phase = receiveProgressFlow.value[key]?.process
        if (closed || phase == PROCESS_MERGING || phase == PROCESS_ABORTED) return false
        updateProgress(key, info)
        return true
    }

    @Synchronized
    fun updateProgress(key: String, info: ReceivingFileInfo) {
        if (closed || key.isBlank()) return
        val current = receiveProgressFlow.value
        val previous = current[key]
        if (info.process == PROCESS_COMPLETED) {
            receiveProgressFlow.value = current - key
            return
        }
        if (info.process == PROCESS_ABORTED && info.currentChunkIndex == 0 &&
            (previous == null || previous.process == PROCESS_MERGING || previous.process == PROCESS_MERGE_ERROR)
        ) return
        val chunkUpdate = info.process == PROCESS_CHUNK || info.process == PROCESS_CHUNK_ERROR
        if (previous?.process == PROCESS_ABORTED && info.process != PROCESS_CHUNK) return

        val progress = if (previous?.process == PROCESS_MERGING && chunkUpdate) previous else info
        val count = if (info.process == PROCESS_CHUNK) info.currentChunkIndex
        else max(previous?.currentChunkIndex ?: 0, info.currentChunkIndex)
        val total = if (info.process == PROCESS_CHUNK) info.totalChunks
        else previous?.totalChunks?.takeIf { it > 0 } ?: info.totalChunks
        val updated = current + (key to progress.copy(
            currentChunkIndex = count,
            totalChunks = total,
        ))
        val finished = updated.filterValues {
            it.process == PROCESS_ABORTED || it.process == PROCESS_MERGE_ERROR || it.process == PROCESS_CHUNK_ERROR
        }.keys
        receiveProgressFlow.value = updated - finished.take(max(0, finished.size - 12)).toSet()
    }

    @Suppress("DEPRECATION")
    fun publish(temp: File, name: String, expectedBuild: ApkBuildInfo?): ReceivedApk {
        val archive = requireNotNull(Globals.app.packageManager.getPackageArchiveInfo(temp.absolutePath, 0)) {
            Globals.getString(R.string.mydroid_apk_invalid)
        }
        val buildInfo = ApkBuildInfo(
            archive.packageName,
            archive.versionName,
            archive.longVersionCode,
            expectedBuild?.variantName,
            temp.readApkSha256(),
        )
        require(expectedBuild == null || expectedBuild == buildInfo) {
            Globals.getString(R.string.mydroid_apk_build_mismatch)
        }
        synchronized(this) {
            check(!closed) { "APK receive session is closed" }
            synchronized(filesLock) {
                val target = TransferFiles.publish(temp, name, File(receivedDir, UUID.randomUUID().toString()))
                val received = ReceivedApk(target, buildInfo)
                val (replaced, retained) = cachedFilesFlow.value.partition { it.buildInfo.applicationId == buildInfo.applicationId }
                cachedFilesFlow.value = listOf(received) + retained
                handledFiles.value -= replaced.map { it.file }.toSet()
                replaced.forEach { deleteCachedFile(it.file) }
                return received
            }
        }
    }

    @Synchronized
    fun close() {
        if (closed) return
        closed = true
        receiveProgressFlow.value = emptyMap()
        activeChunkRequestCount.value = 0
    }

    fun clearTemporaryFiles() {
        Globals.mainScope.launchOnIOThread {
            if (!tempDir.deleteRecursively()) loge { "Cannot remove APK receive temporary directory" }
        }
    }

    companion object {
        private val filesLock = Any()
        private val cacheDirectory get() = File(Globals.app.cacheDir, "shared/apkReceive")
        private val receivedDir get() = File(cacheDirectory, "received")
        private val cachedFilesFlow = MutableStateFlow<List<ReceivedApk>>(emptyList())
        val filesFlow = cachedFilesFlow.asStateFlow()
        private val handledFiles = MutableStateFlow<Set<File>>(emptySet())
        val handledFilesFlow = handledFiles.asStateFlow()
        private val receiveProgressFlow = MutableStateFlow<Map<String, ReceivingFileInfo>>(emptyMap())
        val progressFlow = receiveProgressFlow.asStateFlow()
        private val activeChunkRequestCount = MutableStateFlow(0)
        val activeChunkRequestCountFlow = activeChunkRequestCount.asStateFlow()

        // 只清理进入本页前遗留的数据，各次打开保留本进程已收完的 APK。
        private val cacheRoot: File by lazy {
            synchronized(filesLock) {
                cacheDirectory.apply {
                    val oldFiles = File(this, "received").walkTopDown().count(File::isFile)
                    val oldTempFiles = File(this, "tmp").walkTopDown().count(File::isFile)
                    logd { "Clear APK receive cache: $oldFiles completed files, $oldTempFiles temporary files" }
                    if (!deleteRecursively() || !mkdirs()) throw IOException("Cannot prepare APK receive cache")
                }
            }
        }

        suspend fun prepareCache() {
            withContext(Dispatchers.IO) { cacheRoot }
        }

        fun openCurrentApk(apk: ReceivedApk): FileInputStream? = synchronized(filesLock) {
            if (apk !in cachedFilesFlow.value) null else apk.file.inputStream()
        }

        fun markHandled(apk: ReceivedApk) {
            synchronized(filesLock) {
                if (apk in cachedFilesFlow.value) handledFiles.value += apk.file
            }
        }

        fun commitCurrentApk(apk: ReceivedApk, commit: () -> Unit): Boolean = synchronized(filesLock) {
            if (apk !in cachedFilesFlow.value) return@synchronized false
            commit()
            true
        }

        fun removeInstalled(path: String?) {
            if (path == null) return
            synchronized(filesLock) {
                val file = File(path).absoluteFile
                deleteCachedFile(file)
                cachedFilesFlow.update { files -> files.filterNot { it.file == file } }
                handledFiles.value -= file
            }
        }

        private fun deleteCachedFile(file: File) {
            try {
                val canonical = file.canonicalFile
                val folder = canonical.parentFile ?: return
                if (folder.parentFile != receivedDir.canonicalFile ||
                    !canonical.extension.equals("apk", ignoreCase = true)
                ) return
                if (canonical.exists() && !canonical.delete()) {
                    loge { "Cannot remove received APK ${canonical.name}" }
                    return
                }
                folder.delete()
            } catch (e: IOException) {
                loge { "Cannot resolve received APK path: ${e.message}" }
            }
        }
    }
}
