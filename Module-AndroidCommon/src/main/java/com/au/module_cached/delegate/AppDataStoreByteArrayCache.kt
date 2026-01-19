package com.au.module_cached.delegate

import com.au.module_cached.AppDataStore

class AppDataStoreByteArrayCache(key:String, defaultValue:ByteArray = EMPTY_BYTE_ARRAY, cacheFileName: String? = null)
    : IDSReadMoreWriteLessCacheProperty<ByteArray>(key, defaultValue), IDataStoreWrap by DataStoreWrap(cacheFileName) {
    companion object {
        private val EMPTY_BYTE_ARRAY = byteArrayOf()
    }

    @Deprecated("Use readSuspend instead")
    override fun read(key: String, defaultValue: ByteArray): ByteArray {
        return AppDataStore.readByteArrayBlocked(key, defaultValue, dataStore) ?: defaultValue
    }

    override suspend fun readSuspend(key: String, defaultValue: ByteArray): ByteArray {
        return AppDataStore.readByteArray(key, defaultValue, dataStore) ?: defaultValue
    }

    override fun save(key: String, value: ByteArray) {
        return AppDataStore.saveByteArray(key, value, dataStore)
    }
}
