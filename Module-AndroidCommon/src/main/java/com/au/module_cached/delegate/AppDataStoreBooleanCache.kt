package com.au.module_cached.delegate

import com.au.module_cached.AppDataStore

class AppDataStoreBooleanCache(key:String, defaultValue:Boolean, cacheFileName: String? = null)
        : IDSReadMoreWriteLessCacheProperty<Boolean>(key, defaultValue), IDataStoreWrap by DataStoreWrap(cacheFileName) {
    @Deprecated("Use readSuspend instead")
    override fun read(key: String, defaultValue: Boolean): Boolean {
        return AppDataStore.readBooleanBlocked(key, defaultValue, dataStore)
    }

    override suspend fun readSuspend(key: String, defaultValue: Boolean): Boolean {
        return AppDataStore.readBoolean(key, defaultValue, dataStore)
    }

    override fun save(key: String, value: Boolean) {
        return AppDataStore.saveBoolean(key, value, dataStore)
    }
}
