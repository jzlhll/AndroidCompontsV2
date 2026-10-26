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
import org.json.JSONObject
import com.au.module_android.Globals
import com.au.module_cached.AppDataStore
import com.au.module_cached.AppDataStore.globalDataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey

/** 文件仓库只扫描元数据，按路径、大小和修改时间复用传输完成后的摘要快照。 */
class GlobalFileListRepoObj {
    private val _fileListStateFlow = MutableStateFlow<StatusState<List<MergedFileInfo>>>(StatusState.Loading)
    val fileListStateFlow: StateFlow<StatusState<List<MergedFileInfo>>> = _fileListStateFlow.asStateFlow()

    private val mutex = Mutex()
    private data class CachedFile(val length: Long, val modified: Long, val info: MergedFileInfo)
    private var cache = mutableMapOf<String, CachedFile>()
    private var digestSnapshot: JSONObject? = null
    private val digestKey = stringPreferencesKey("mydroid_received_md5")

    private fun digestSnapshot(): JSONObject {
        digestSnapshot?.let { return it }
        val text = AppDataStore.readStringBlocked("mydroid_received_md5", "").orEmpty()
        val snapshot = try { if (text.isEmpty()) JSONObject() else JSONObject(text) }
        catch (e: Exception) { JSONObject() }
        digestSnapshot = snapshot
        return snapshot
    }

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
            val snapshot = digestSnapshot()
            snapshot.put(file.absolutePath, JSONObject().apply {
                put("length", length)
                put("modified", file.lastModified())
                put("md5", md5)
            })
            Globals.app.globalDataStore.edit { it[digestKey] = snapshot.toString() }
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
                    val saved = digestSnapshot().optJSONObject(it.absolutePath)
                    val md5 = if (saved != null && saved.optLong("length") == length &&
                        saved.optLong("modified") == modified) saved.optString("md5") else ""
                    MergedFileInfo.fromCacheFile(it, formatSize(length), md5)
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
