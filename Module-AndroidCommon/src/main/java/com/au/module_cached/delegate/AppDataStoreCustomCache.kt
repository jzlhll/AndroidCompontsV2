package com.au.module_cached.delegate

import com.au.module_android.utils.IReadMoreWriteLessCacheProperty

/**
 * 自定义泛型的缓存
 * 2个转换器，不得报错。
 */
class AppDataStoreCustomCache<T:Any> (
    key: String,
    val converter: (T) -> String,
    val reverter: (String) -> T,
    val defaultValue:T,
    cacheFileName: String? = null
) : IReadMoreWriteLessCacheProperty<T>(key, defaultValue) {

    private var cache by AppDataStoreStringCache(key, "", cacheFileName)
    override fun read(key: String, defaultValue: T): T {
        val str = cache
        if (str.isNotEmpty()) {
            return reverter(str)
        }
        return defaultValue
    }

    override fun save(key: String, value: T) {
        cache = converter(value)
    }
}