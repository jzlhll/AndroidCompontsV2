package com.allan.mydroid.globals

import android.content.Intent
import android.net.Uri
import com.allan.mydroid.CHECK_URI_PERMISSION
import com.allan.mydroid.PICKER_NEED_PERMISSION
import com.allan.mydroid.beansinner.FROM_LOCAL
import com.allan.mydroid.beansinner.FROM_PICKER
import com.allan.mydroid.beansinner.FROM_SHARE_IN
import com.allan.mydroid.beansinner.MergedFileInfo
import com.allan.mydroid.beansinner.ShareInBean
import com.au.module_android.Globals
import com.au.module_android.log.logdNoFile
import com.au.module_android.simpleflow.StatusState
import com.au.module_android.utils.Md5Util
import com.au.module_android.utils.asOrNull
import com.au.module_android.utils.launchOnThread
import com.au.module_cached.AppDataStore
import com.au.module_gson.fromGson
import com.au.module_gson.toGsonString
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ShareInUrisObj {
    /**
     * 已经loaded beans。用于透传给后续显示
     */
    var shareInAndReceiveBeans : List<ShareInBean>? = null

    /**
     * 这个就全局存活。不重启app不做处理。
     * 从shareReceiver activity处接收数据
     * key是uriUuid
     */
    private var mSendUriMap: HashMap<String, ShareInBean>?= null
    private val sendUriMap : HashMap<String, ShareInBean>
        get() {
            val m = mSendUriMap
            if (m == null) {
                val map = initCacheSendUriMap()
                mSendUriMap = map
                return map
            } else {
                return m
            }
        }

    private val _fileListState = MutableStateFlow<StatusState<List<MergedFileInfo>>>(initLoadFileListState())
    val fileListState: StateFlow<StatusState<List<MergedFileInfo>>> = _fileListState.asStateFlow()

    private fun initCacheSendUriMap() : HashMap<String, ShareInBean> {
        var time = System.currentTimeMillis()
        try {
            val json = AppDataStore.readBlocked("mydroid_sendUriMap", "")
            logdNoFile{"load cache sendUri Map json: $json"}
            if (json.isEmpty()) {
                return hashMapOf()
            }
            val map: HashMap<String, ShareInBean>? = json.fromGson()
            val hostUris = appHostPermissions()
            val needDeleteKeys = mutableListOf<String>()
            map?.forEach { k, v->
                if (v.from == FROM_PICKER) {
                    val found = hostUris.find { it == v.uri }
                    if (found == null) {
                        needDeleteKeys.add(k)
                    }
                }
            }
            needDeleteKeys.forEach {
                map?.remove(it)
            }

            logdNoFile{"load cache sendUri Map json2: $map"}
            return map ?: hashMapOf()
        } finally {
            time = System.currentTimeMillis() - time
            logdNoFile{"load cache sendUri Map time: $time"}
        }
    }

    private fun initLoadFileListState() = StatusState.Success(runBlocking { loadFileListInner() })

    private fun updateSendUriMap(map: HashMap<String, ShareInBean>?) {
        val fixMap = map ?: hashMapOf()
        mSendUriMap = fixMap
        Globals.mainScope.launchOnThread {
            AppDataStore.save("mydroid_sendUriMap", fixMap.toGsonString())
        }
    }

    /**
     * 点击移除
     */
    fun deleteUris(uuids : List<String>) {
        val sendMap = sendUriMap
        uuids.forEach {
            sendMap.remove(it)
        }
        updateSendUriMap(sendMap)
    }

    /**
     * 当接收列表，添加新的uri
     */
    suspend fun addShareInUris(uris:List<Uri>, from:String) {
        val newUris = mutableListOf<ShareInBean>()
        val oldUris = sendUriMap.values
        oldUris.forEach {
            newUris.add(it)
        }
        uris.forEach { uri->
            val found = oldUris.find { it.uri == uri }
            if (found == null) {
                val infoEx = ShareInBean.convert(uri, from)
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
        val files = fileListState.value.asOrNull<StatusState.Success<List<MergedFileInfo>>>()?.data ?: mutableListOf()
        val receivedShareInBeans = files.map {
            val bean = ShareInBean.convert(it, FROM_LOCAL)
            bean.isLocalReceiver = true
            bean
        }
        shareInAndReceiveBeans = receivedShareInBeans.plus(shareInBeans)
        return shareInAndReceiveBeans!!
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

    private fun loadFileListInner(): ArrayList<MergedFileInfo> {
        val nanoMergedDir = File(nanoTempCacheMergedDir())
        val fileList = ArrayList<MergedFileInfo>()
        if (nanoMergedDir.exists()) {
            nanoMergedDir.listFiles()?.forEach {
                fileList.add(MergedFileInfo(it, Md5Util.getFileMD5(it.absolutePath), formatSize(it.length())))
            }
        }
        fileList.sortByDescending { it.file.lastModified() }
        return fileList
    }

    /**
     * 真正需要更新的时候，更新即可。
     */
    suspend fun reloadFileList() {
        val fileList = loadFileListInner()
        delay(100)
        _fileListState.value = StatusState.Success(fileList)
    }

    /**
     * 是不是有这个的权限呢
     */
    fun isHostThisUri(shareInBean: ShareInBean) : Boolean {
        if (!CHECK_URI_PERMISSION) {
            return true
        }

        if (shareInBean.from == FROM_LOCAL || shareInBean.from == FROM_SHARE_IN) {
            return true
        }

        if (shareInBean.from == FROM_PICKER && !PICKER_NEED_PERMISSION) {
            return true
        }

        appHostPermissions().forEach { uri->
            if (uri == shareInBean.uri) {
                return true
            }
        }
        return false
    }

    private fun appHostPermissions() : List<Uri> {
        val list = Globals.app.contentResolver.persistedUriPermissions
        return list.map { it.uri }
    }

    fun takeHostPermission(uri: Uri) {
        if (CHECK_URI_PERMISSION) {
            Globals.app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}