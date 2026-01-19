package com.au.module_cached.delegate

import com.au.module_cached.AppDataStore

class AppDataStoreFloatCache(key:String, defaultValue:Float, cacheFileName: String? = null)
    : IDSReadMoreWriteLessCacheProperty<Float>(key, defaultValue), IDataStoreWrap by DataStoreWrap(cacheFileName) {

    @Deprecated("Use readSuspend instead")
    override fun read(key: String, defaultValue: Float): Float {
        return AppDataStore.readFloatBlocked(key, defaultValue, dataStore)
    }

    override suspend fun readSuspend(key: String, defaultValue: Float): Float {
        return AppDataStore.readFloat(key, defaultValue, dataStore)
    }

    override fun save(key: String, value: Float) {
        return AppDataStore.saveFloat(key, value, dataStore)
    }
}
