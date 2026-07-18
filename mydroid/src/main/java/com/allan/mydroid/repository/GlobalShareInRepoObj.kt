package com.allan.mydroid.repository

import android.net.Uri
import com.allan.mydroid.beansinner.FROM_LOCAL
import com.allan.mydroid.beansinner.FROM_PICKER
import com.allan.mydroid.beansinner.MergedFileInfo
import com.allan.mydroid.beansinner.ShareInBean
import com.au.module_android.Globals
import com.au.module_android.log.logdNoFile
import com.au.module_android.simpleflow.StatusState
import com.au.module_android.utils.asOrNull
import com.au.module_android.utils.launchOnThread
import com.au.module_cached.AppDataStore
import com.au.module_gson.fromGson
import com.au.module_gson.toGsonString

class GlobalShareInRepoObj(
    private val fileListRepository: GlobalFileListRepoObj,
    private val uriPermissionChecker: UriPermissionChecker,
) {
    var shareInAndReceiveBeans: List<ShareInBean>? = null

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
            if (json.isEmpty()) {
                return hashMapOf()
            }
            val map: HashMap<String, ShareInBean>? = json.fromGson()
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
            needDeleteKeys.forEach {
                map?.remove(it)
            }

            logdNoFile { "load cache sendUri Map json2: $map" }
            return map ?: hashMapOf()
        } finally {
            time = System.currentTimeMillis() - time
            logdNoFile { "load cache sendUri Map time: $time" }
        }
    }

    private fun updateSendUriMap(map: HashMap<String, ShareInBean>?) {
        val fixMap = map ?: hashMapOf()
        mSendUriMap = fixMap
        Globals.mainScope.launchOnThread {
            AppDataStore.saveString("mydroid_sendUriMap", fixMap.toGsonString())
        }
    }

    fun deleteUris(uuids: List<String>) {
        val sendMap = sendUriMap
        uuids.forEach {
            sendMap.remove(it)
        }
        updateSendUriMap(sendMap)
    }

    suspend fun addShareInUris(uris: List<Uri>, from: String) {
        val newUris = mutableListOf<ShareInBean>()
        val oldUris = sendUriMap.values
        oldUris.forEach {
            newUris.add(it)
        }
        uris.forEach { uri ->
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

    suspend fun loadShareInAndReceiveBeans(): List<ShareInBean> {
        val shareInBeans = sendUriMap.values
        val files = fileListRepository.fileListStateFlow.value
            .asOrNull<StatusState.Success<List<MergedFileInfo>>>()?.data ?: mutableListOf()
        val receivedShareInBeans = files.map {
            val bean = ShareInBean.convert(it, FROM_LOCAL)
            bean.isLocalReceiver = true
            bean
        }
        shareInAndReceiveBeans = receivedShareInBeans.plus(shareInBeans)
        return shareInAndReceiveBeans!!
    }
}
