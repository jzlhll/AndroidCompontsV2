package com.allan.mydroid.globals

import android.net.Uri
import com.allan.mydroid.beansinner.MergedFileInfo
import com.allan.mydroid.beansinner.ShareInBean
import com.au.module_android.Globals
import com.au.module_android.json.fromJson
import com.au.module_android.json.toJsonString
import com.au.module_android.utils.getFileMD5
import com.au.module_android.utils.launchOnThread
import com.au.module_android.utils.logdNoFile
import com.au.module_cached.AppDataStore
import kotlinx.coroutines.delay
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.collections.forEach

object ShareInUrisObj {
    /**
     * 这个就全局存活。不重启app不做处理。
     * 从shareReceiver activity处接收数据
     * key是uriUuid
     */
    var sendUriMap: HashMap<String, ShareInBean> = loadCacheSendUriMap()

    private fun loadCacheSendUriMap() : HashMap<String, ShareInBean> {
        var time = System.currentTimeMillis()
        try {
            val json = AppDataStore.readBlocked("mydroid_sendUriMap", "")
            logdNoFile{"load cache sendUri Map json: $json"}
            if (json.isEmpty()) {
                return hashMapOf()
            }
            val list: HashMap<String, ShareInBean>? = json.fromJson()
            logdNoFile{"load cache sendUri Map json2: $list"}
            return list ?: hashMapOf()
        } finally {
            time = System.currentTimeMillis() - time
            logdNoFile{"load cache sendUri Map time: $time"}
        }
    }

    private fun updateSendUriMap(map: HashMap<String, ShareInBean>?) {
        val fixMap = map ?: hashMapOf()
        sendUriMap = fixMap
        Globals.mainScope.launchOnThread {
            AppDataStore.save("mydroid_sendUriMap", fixMap.toJsonString())
        }
    }

    /**
     * 点击移除
     */
    fun deleteUris(uuids : List<String>) {
        uuids.forEach {
            sendUriMap.remove(it)
        }
        updateSendUriMap(sendUriMap)
    }

    /**
     * 当接收列表，添加新的uri
     */
    suspend fun addNewUris(uris:List<Uri>) {
        val newUris = mutableListOf<ShareInBean>()

        val oldUris = sendUriMap.values
        oldUris.forEach {
            newUris.add(it)
        }
        uris.forEach { uri->
            val found = oldUris.find { it.uri == uri }
            if (found == null) {
                val infoEx = ShareInBean.to(uri)
                newUris.add(infoEx)
            }
        }

        val hashMap = hashMapOf<String, ShareInBean>()
        newUris.forEach {
            hashMap[it.uriUuid] = it
        }
        updateSendUriMap(hashMap)
    }

    /**
     * 将本地接收到的文件列表，和 share导入的，综合起来
     */
    suspend fun loadShareInAndReceiveBeans() : List<ShareInBean> {
        //导入的文件
        val shareInBeans = sendUriMap.values

        //本地接收的文件，默认不勾选，不允许操作
        val receivedShareInBeans = loadFileList().map {
            val bean = ShareInBean.to(it)
            bean.isLocalReceiver = true
            bean
        }
        return receivedShareInBeans.plus(shareInBeans)
    }

    private const val KEY_EXPORT_HISTORY = "my_droid_export_history_list"

    suspend fun loadExportHistory() : String {
        return AppDataStore.read(KEY_EXPORT_HISTORY, "")
    }

    suspend fun writeNewExportHistory(info:String) {
        val old = loadExportHistory()
        val splits = old.split("\n")
        val fixOld = if (splits.size > 100) {
            val cutList = splits.subList(0, 80)
            cutList.joinToString("\n")
        } else {
            old
        }

        // 获取当前时间戳
        val currentTimeMillis = System.currentTimeMillis()
        // 定义时间格式（例如：2023年10月05日 14:30）
        val formatter = DateTimeFormatter
            .ofPattern("yyyyMMdd HH:mm")
            .withZone(ZoneId.systemDefault()) // 使用系统默认时区
        // 格式化为字符串
        val formattedTime = formatter.format(Instant.ofEpochMilli(currentTimeMillis))

        AppDataStore.save(KEY_EXPORT_HISTORY, "($formattedTime) $info\n\n$fixOld")
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

    suspend fun loadFileList() : List<MergedFileInfo> {
        delay(0)
        val nanoMergedDir = File(nanoTempCacheMergedDir())
        val fileList = ArrayList<MergedFileInfo>()
        if (nanoMergedDir.exists()) {
            nanoMergedDir.listFiles()?.forEach {
                fileList.add(MergedFileInfo(it, getFileMD5(it.absolutePath), formatSize(it.length())))
            }
        }
        fileList.sortByDescending { it.file.lastModified() }
        return fileList
    }
}