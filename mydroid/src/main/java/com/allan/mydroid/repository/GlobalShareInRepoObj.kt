package com.allan.mydroid.repository

import android.net.Uri
import com.allan.mydroid.beansinner.FROM_LOCAL
import com.allan.mydroid.beansinner.FROM_SHARE_IN
import com.allan.mydroid.beansinner.FROM_PICKER
import com.allan.mydroid.beansinner.ShareInBean
import com.allan.mydroid.beansinner.LEGACY_IMPORT_BATCH_ID
import com.allan.mydroid.beansinner.groupImportBatches
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
import androidx.annotation.Keep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.au.module_android.utilsmedia.formatBytes
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.au.module_cached.AppDataStore
import com.au.module_gson.fromGson
import com.au.module_gson.toGsonString

/** 单次下载结果只在发送响应结束后发布，未完成时不包含摘要。 */
@Keep
data class DownloadTransfer(
    val transferId: String,
    val uriUuid: String,
    val state: String = "sending",
    val md5: String = "",
    val importBatchId: String? = null
)

/** 串行维护分享列表及持久化快照，供页面和 HTTP 下载共用。 */
class GlobalShareInRepoObj(
    private val fileListRepository: GlobalFileListRepoObj,
    private val uriPermissionChecker: UriPermissionChecker,
) {
    @Volatile
    var shareInAndReceiveBeans: List<ShareInBean>? = null
        private set
    private val mutex = Mutex()
    private val _downloadTransfersFlow = MutableStateFlow<Map<String, DownloadTransfer>>(emptyMap())
    val downloadTransfersFlow = _downloadTransfersFlow.asStateFlow()

    fun startDownload(transferId: String, uriUuid: String, importBatchId: String?) {
        _downloadTransfersFlow.update { previous ->
            val existing = previous[transferId]
            check(existing == null || (existing.uriUuid == uriUuid && existing.state != "sending")) {
                "Download session already active"
            }
            val next = LinkedHashMap(previous)
            next.remove(transferId)
            if (next.size >= 256) {
                val completed = next.entries.firstOrNull { it.value.state != "sending" }
                check(completed != null) { "Too many active downloads" }
                next.remove(completed.key)
            }
            next[transferId] = DownloadTransfer(transferId, uriUuid, importBatchId = importBatchId)
            next
        }
    }

    fun finishDownload(transferId: String, md5: String?) {
        _downloadTransfersFlow.update { previous ->
            val item = previous[transferId] ?: return@update previous
            previous + (transferId to item.copy(state = if (md5 == null) "failed" else "completed", md5 = md5.orEmpty()))
        }
    }

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

    private suspend fun loadSendUriMap(): HashMap<String, ShareInBean> {
        val map = sendUriMap
        val legacy = map.entries.filter { it.value.importBatchId.isNullOrBlank() }.sortedBy { it.key }
        if (legacy.isEmpty()) return map
        val next = HashMap(map)
        // 旧记录没有可靠的导入时间，保留下载标识和 URI，以固定批次一次性迁移并落盘。
        legacy.forEachIndexed { index, entry ->
            next[entry.key] = entry.value.copy(importBatchId = LEGACY_IMPORT_BATCH_ID,
                importedAt = 0L, importOrder = index)
        }
        updateSendUriMap(next)
        return next
    }

    suspend fun deleteUris(uuids: List<String>) = withIOThread {
        mutex.withLock {
            val next = HashMap(loadSendUriMap())
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
            val next = HashMap(loadSendUriMap())
            val batchId = UUID.randomUUID().toString()
            val importedAt = System.currentTimeMillis()
            val copiedFiles = mutableListOf<File>()
            val replacedBeans = mutableListOf<ShareInBean>()
            var committed = false
            try {
                for ((index, uri) in uris.distinct().withIndex()) {
                    val importKey = UUID.nameUUIDFromBytes("share:$uri".toByteArray(Charsets.UTF_8))
                        .toString().replace("-", "")
                    // 分享副本的 URI 已改变，同时按原始来源标识匹配，兼容旧记录与跨入口重复导入。
                    val replaced = next.filter { (key, bean) ->
                        bean.sourceUriKey == importKey || key == importKey || bean.uri == uri
                    }
                    // uriUuid 标识同一来源的逻辑文件，批次标识隔离每次重新导入的下载结果。
                    val uriUuid = replaced.values.firstOrNull()?.uriUuid ?: importKey
                    var bean = ShareInBean.convert(uri, from).copy(uriUuid = uriUuid, importBatchId = batchId,
                        importedAt = importedAt, importOrder = index, sourceUriKey = importKey)
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
                        bean = bean.copy(uri = Uri.fromFile(target), fileSize = target.length(),
                            fileSizeStr = formatBytes(target.length()))
                    }
                    replaced.forEach { (key, previous) ->
                        next.remove(key)
                        replacedBeans.add(previous)
                    }
                    next[bean.uriUuid] = bean
                }
                currentCoroutineContext().ensureActive()
                withContext(NonCancellable) {
                    updateSendUriMap(next)
                    committed = true
                    shareInAndReceiveBeans = shareInAndReceiveBeans?.let { previous ->
                        groupImportBatches(next.values.toList()).flatten() + previous.filter { it.isLocalReceiver }
                    }
                    // 新批次提交后再清理被替换副本，失败或取消时仍可使用原记录。
                    val retainedUris = next.values.map { it.uri }.toSet()
                    replacedBeans.forEach { previous ->
                        if (previous.uri.scheme == "file" && previous.uri !in retainedUris) {
                            val file = File(previous.uri.path.orEmpty())
                            if (file.parentFile == importDir) file.delete()
                        }
                    }
                }
            } finally {
                if (!committed) copiedFiles.forEach { it.delete() }
            }
        }
    }

    suspend fun loadShareInAndReceiveBeans(): List<ShareInBean> = withIOThread {
        mutex.withLock {
            val shareInBeans = groupImportBatches(loadSendUriMap().values.toList()).flatten()
            val files = fileListRepository.reloadFileList()
            val receivedShareInBeans = files.map {
                ShareInBean.convert(it, FROM_LOCAL).also { bean -> bean.isLocalReceiver = true }
            }
            val result = shareInBeans + receivedShareInBeans
            shareInAndReceiveBeans = result
            result
        }
    }
}
