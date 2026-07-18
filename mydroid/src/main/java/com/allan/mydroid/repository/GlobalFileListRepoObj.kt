package com.allan.mydroid.repository

import com.allan.mydroid.beansinner.MergedFileInfo
import com.allan.mydroid.globals.nanoTempCacheMergedDir
import com.au.module_android.simpleflow.StatusState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class GlobalFileListRepoObj {
    private val _fileListStateFlow = MutableStateFlow<StatusState<List<MergedFileInfo>>>(StatusState.Loading)
    val fileListStateFlow: StateFlow<StatusState<List<MergedFileInfo>>> = _fileListStateFlow.asStateFlow()

    init {
        _fileListStateFlow.value = StatusState.Success(loadFileListInner())
    }

    suspend fun reloadFileList() {
        val fileList = loadFileListInner()
        _fileListStateFlow.value = StatusState.Success(fileList)
    }

    private fun loadFileListInner(): ArrayList<MergedFileInfo> {
        val nanoMergedDir = File(nanoTempCacheMergedDir())
        val fileList = ArrayList<MergedFileInfo>()
        if (nanoMergedDir.exists()) {
            nanoMergedDir.listFiles()?.forEach {
                fileList.add(MergedFileInfo.fromCacheFile(it, formatSize(it.length())))
            }
        }
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
