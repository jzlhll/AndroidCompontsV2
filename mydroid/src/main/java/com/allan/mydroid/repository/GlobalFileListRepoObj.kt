package com.allan.mydroid.repository

import com.allan.mydroid.beansinner.MergedFileInfo
import com.allan.mydroid.globals.nanoTempCacheMergedDir
import com.au.module_android.simpleflow.StatusState
import com.au.module_android.utils.withIOThread
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/** 文件仓库在 IO 线程串行刷新，按路径、大小和修改时间复用已计算的摘要。 */
class GlobalFileListRepoObj {
    private val _fileListStateFlow = MutableStateFlow<StatusState<List<MergedFileInfo>>>(StatusState.Loading)
    val fileListStateFlow: StateFlow<StatusState<List<MergedFileInfo>>> = _fileListStateFlow.asStateFlow()

    private val mutex = Mutex()
    private data class CachedFile(val length: Long, val modified: Long, val info: MergedFileInfo)
    private var cache = mutableMapOf<String, CachedFile>()

    suspend fun reloadFileList(): List<MergedFileInfo> = withIOThread {
        mutex.withLock {
            val fileList = loadFileListInner()
            _fileListStateFlow.value = StatusState.Success(fileList)
            fileList
        }
    }

    suspend fun addReceivedFile(file: File, md5: String) = withIOThread {
        mutex.withLock {
            val length = file.length()
            val info = MergedFileInfo(file, md5, formatSize(length))
            cache[file.absolutePath] = CachedFile(length, file.lastModified(), info)
            // 落盘事件不依赖页面订阅，复用合并时已经校验过的摘要。
            _fileListStateFlow.value = StatusState.Success(cache.values.map { it.info }
                .sortedByDescending { it.file.lastModified() })
        }
    }

    private suspend fun loadFileListInner(): ArrayList<MergedFileInfo> {
        val nanoMergedDir = File(nanoTempCacheMergedDir())
        val fileList = ArrayList<MergedFileInfo>()
        val nextCache = mutableMapOf<String, CachedFile>()
        if (nanoMergedDir.exists()) {
            nanoMergedDir.listFiles()?.forEach {
                currentCoroutineContext().ensureActive()
                if (!it.isFile) return@forEach
                val length = it.length()
                val modified = it.lastModified()
                val cached = cache[it.absolutePath]
                val info = if (cached != null && cached.length == length && cached.modified == modified) {
                    cached.info
                } else {
                    MergedFileInfo.fromCacheFile(it, formatSize(length))
                }
                fileList.add(info)
                nextCache[it.absolutePath] = CachedFile(length, modified, info)
            }
        }
        cache = nextCache
        fileList.sortByDescending { it.file.lastModified() }
        return fileList
    }

    fun formatSize(bytes: Long): String {
        val units = listOf("B", "KB", "MB", "GB")
        var size = bytes.toDouble()
        var unitIndex = 0

        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }
        return "%.2f %s".format(size, units[unitIndex])
    }
}
