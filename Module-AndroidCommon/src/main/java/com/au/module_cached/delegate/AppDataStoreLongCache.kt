package com.au.module_cached.delegate

import com.au.module_cached.AppDataStore

class AppDataStoreLongCache(key:String, defaultValue:Long, cacheFileName: String? = null)
    : IDSReadMoreWriteLessCacheProperty<Long>(key, defaultValue), IDataStoreWrap by DataStoreWrap(cacheFileName) {

    @Deprecated("Use readSuspend instead")
    override fun read(key: String, defaultValue: Long): Long {
        return AppDataStore.readLongBlocked(key, defaultValue, dataStore)
    }

    override suspend fun readSuspend(key: String, defaultValue: Long): Long {
        return AppDataStore.readLong(key, defaultValue, dataStore)
    }

    override fun save(key: String, value: Long) {
        return AppDataStore.saveLong(key, value, dataStore)
    }
}
