package com.au.module_cached.delegate

import com.au.module_cached.AppDataStore

class AppDataStoreIntCache(key:String, defaultValue:Int, cacheFileName: String? = null)
    : IDSReadMoreWriteLessCacheProperty<Int>(key, defaultValue), IDataStoreWrap by DataStoreWrap(cacheFileName) {

    @Deprecated("Use readSuspend instead")
    override fun read(key: String, defaultValue: Int): Int {
        return AppDataStore.readIntBlocked(key, defaultValue, dataStore)
    }

    override suspend fun readSuspend(key: String, defaultValue: Int): Int {
        return AppDataStore.readInt(key, defaultValue, dataStore)
    }

    override fun save(key: String, value: Int) {
        return AppDataStore.saveInt(key, value, dataStore)
    }
}
