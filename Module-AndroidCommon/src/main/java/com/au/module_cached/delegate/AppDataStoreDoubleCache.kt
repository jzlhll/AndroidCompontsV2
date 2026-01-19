package com.au.module_cached.delegate

import com.au.module_cached.AppDataStore

class AppDataStoreDoubleCache(key:String, defaultValue:Double, cacheFileName: String? = null)
    : IDSReadMoreWriteLessCacheProperty<Double>(key, defaultValue), IDataStoreWrap by DataStoreWrap(cacheFileName) {

    @Deprecated("Use readSuspend instead")
    override fun read(key: String, defaultValue: Double): Double {
        return AppDataStore.readDoubleBlocked(key, defaultValue, dataStore)
    }

    override suspend fun readSuspend(key: String, defaultValue: Double): Double {
        return AppDataStore.readDouble(key, defaultValue, dataStore)
    }

    override fun save(key: String, value: Double) {
        return AppDataStore.saveDouble(key, value, dataStore)
    }
}
