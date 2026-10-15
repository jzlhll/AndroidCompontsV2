package com.allan.mydroid.repository

import android.net.Uri
import com.allan.mydroid.beansinner.FROM_LOCAL
import com.allan.mydroid.beansinner.FROM_SHARE_IN
import com.allan.mydroid.beansinner.FROM_PICKER
import com.allan.mydroid.beansinner.ShareInBean
import com.au.module_android.Globals
import com.au.module_android.log.logdNoFile
import com.au.module_android.utils.withIOThread
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.au.module_cached.AppDataStore.globalDataStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import com.au.module_android.utilsmedia.formatBytes
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.au.module_cached.AppDataStore
import com.au.module_gson.fromGson
import com.au.module_gson.toGsonString

/** 串行维护分享列表及持久化快照，供页面和 HTTP 下载共用。 */
class GlobalShareInRepoObj(
    private val fileListRepository: GlobalFileListRepoObj,
    private val uriPermissionChecker: UriPermissionChecker,
) {
    @Volatile
    var shareInAndReceiveBeans: List<ShareInBean>? = null
        private set
    private val mutex = Mutex()

    private val importDir get() = File(Globals.app.filesDir, "share-imports")

    private var mSendUriMap: HashMap<String, ShareInBean>? = null
    private val sendUriMap: HashMap<String, ShareInBean>
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

    private fun initCacheSendUriMap(): HashMap<String, ShareInBean> {
        var time = System.currentTimeMillis()
        try {
            val json = AppDataStore.readStringBlocked("mydroid_sendUriMap", "") ?: ""
            logdNoFile { "load cache sendUri Map json: $json" }
            val map: HashMap<String, ShareInBean>? = if (json.isEmpty()) hashMapOf() else json.fromGson()
            val hostUris = Globals.app.contentResolver.persistedUriPermissions.map { it.uri }
            val needDeleteKeys = mutableListOf<String>()
            map?.forEach { k, v ->
                if (v.from == FROM_PICKER) {
                    val found = hostUris.find { it == v.uri }
                    if (found == null) {
                        needDeleteKeys.add(k)
                    }
                }
            }
            map?.forEach { (key, bean) ->
                if (bean.from == FROM_SHARE_IN && bean.uri.scheme == "file" &&
                    !File(bean.uri.path.orEmpty()).isFile) needDeleteKeys.add(key)
            }
            needDeleteKeys.forEach {
                map?.remove(it)
            }

            val retained = map?.values?.map { it.uri.path }?.toSet().orEmpty()
            importDir.listFiles()?.filter { it.absolutePath !in retained }?.forEach { it.delete() }
            logdNoFile { "load cache sendUri Map json2: $map" }
            return map ?: hashMapOf()
        } finally {
            time = System.currentTimeMillis() - time
            logdNoFile { "load cache sendUri Map time: $time" }
        }
    }

    private suspend fun updateSendUriMap(map: HashMap<String, ShareInBean>) {
        val json = map.toGsonString()
        // 等待持久化完成后再发布，防止多个异步写入乱序覆盖。
        Globals.app.globalDataStore.edit { it[stringPreferencesKey("mydroid_sendUriMap")] = json }
        mSendUriMap = map
    }

    suspend fun deleteUris(uuids: List<String>) = withIOThread {
        mutex.withLock {
            val next = HashMap(sendUriMap)
            val removed = uuids.mapNotNull { next.remove(it) }
            updateSendUriMap(next)
            shareInAndReceiveBeans = shareInAndReceiveBeans?.filterNot { it.uriUuid in uuids }
            removed.forEach { bean ->
                if (bean.uri.scheme == "file") {
                    val file = File(bean.uri.path.orEmpty())
                    if (file.parentFile == importDir) file.delete()
                }
            }
        }
    }

    suspend fun addShareInUris(uris: List<Uri>, from: String) = withIOThread {
        mutex.withLock {
            val next = HashMap(sendUriMap)
            val copiedFiles = mutableListOf<File>()
            var committed = false
            try {
                for (uri in uris.distinct()) {
                    if (next.values.any { it.uri == uri }) continue
                    val importKey = if (from == FROM_SHARE_IN) {
                        UUID.nameUUIDFromBytes("share:$uri".toByteArray(Charsets.UTF_8)).toString().replace("-", "")
                    } else null
                    // 副本 URI 与来源不同，保留按来源去重，页面重建后重试也不会重复导入。
                    if (importKey != null && next.containsKey(importKey)) continue
                    var bean = ShareInBean.convert(uri, from)
                    if (from == FROM_SHARE_IN) {
                        // 外部分享的读授权随来源任务结束，先保存独立副本再关闭导入页。
                        check(importDir.isDirectory || importDir.mkdirs()) { "Cannot create import directory" }
                        val target = File.createTempFile("import-", ".bin", importDir)
                        copiedFiles.add(target)
                        Globals.app.contentResolver.openInputStream(uri).use { input ->
                            checkNotNull(input) { "Cannot open shared file" }
                            target.outputStream().use { output ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val count = input.read(buffer)
                                    if (count == -1) break
                                    output.write(buffer, 0, count)
                                }
                            }
                        }
                        bean = bean.copy(uriUuid = checkNotNull(importKey), uri = Uri.fromFile(target), fileSize = target.length(),
                            fileSizeStr = formatBytes(target.length()))
                    }
                    next[bean.uriUuid] = bean
                }
                currentCoroutineContext().ensureActive()
                withContext(NonCancellable) {
                    updateSendUriMap(next)
                    committed = true
                }
            } finally {
                if (!committed) copiedFiles.forEach { it.delete() }
            }
        }
    }

    suspend fun loadShareInAndReceiveBeans(): List<ShareInBean> = withIOThread {
        mutex.withLock {
            val shareInBeans = sendUriMap.values.toList()
            val files = fileListRepository.reloadFileList()
            val receivedShareInBeans = files.map {
                ShareInBean.convert(it, FROM_LOCAL).also { bean -> bean.isLocalReceiver = true }
            }
            val result = receivedShareInBeans + shareInBeans
            shareInAndReceiveBeans = result
            result
        }
    }
}
