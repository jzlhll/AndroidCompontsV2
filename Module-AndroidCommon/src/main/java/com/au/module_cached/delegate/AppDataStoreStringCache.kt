package com.au.module_cached.delegate

import com.au.module_cached.AppDataStore

class AppDataStoreStringCache(key:String, defaultValue:String, cacheFileName: String? = null)
        : IDSReadMoreWriteLessCacheProperty<String>(key, defaultValue), IDataStoreWrap by DataStoreWrap(cacheFileName){

    @Deprecated("Use readSuspend instead")
    override fun read(key: String, defaultValue: String): String {
        return AppDataStore.readStringBlocked(key, defaultValue, dataStore) ?: defaultValue
    }

    override suspend fun readSuspend(key: String, defaultValue: String): String {
        return AppDataStore.readString(key, defaultValue, dataStore) ?: defaultValue
    }

    override fun save(key: String, value: String) {
        return AppDataStore.saveString(key, value, dataStore)
    }
}
