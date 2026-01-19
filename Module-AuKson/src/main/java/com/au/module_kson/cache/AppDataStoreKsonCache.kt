package com.au.module_kson.cache

import com.au.module_android.utils.IReadMoreWriteLessCacheProperty
import com.au.module_android.utils.ignoreError
import com.au.module_cached.delegate.AppDataStoreStringCache
import com.au.module_cached.delegate.IDSReadMoreWriteLessCacheProperty
import com.au.module_kson.kson
import kotlinx.serialization.KSerializer

/**
 * 通过json string存入到AppDataStoreStringCache。实现转换Json。
 * 不能直接修改你的Class里面的内容是不会给你保存的。 你需要·等于一下·就能保存了。
 *
 * warn：注意目前只传入了一层 class，因此不能支持嵌套泛型。尽量简约。
 */
class AppDataStoreKsonCache<T : Any> (
    key: String,
    defaultValue: T,
    private val serializer: KSerializer<T>,
    cacheFileName: String? = null
) : IDSReadMoreWriteLessCacheProperty<T>(key, defaultValue) {

    private var cache by AppDataStoreStringCache(key, kson.encodeToString(serializer, defaultValue), cacheFileName)

    override fun read(key: String, defaultValue: T): T {
        val jsonStr = cache
        if (jsonStr.isNotEmpty()) {
            return ignoreError { kson.decodeFromString(serializer, jsonStr) } ?: defaultValue
        }
        return defaultValue
    }

    override fun save(key: String, value: T) {
        val c = kson.encodeToString(serializer, value)
        cache = c
    }

    override suspend fun readSuspend(key: String, defaultValue: T): T {
        val jsonStr = cache
        if (jsonStr.isNotEmpty()) {
            return ignoreError { kson.decodeFromString(serializer, jsonStr) } ?: defaultValue
        }
        return defaultValue
    }

}