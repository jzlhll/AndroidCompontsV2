package com.au.module_gson.cache

import com.au.module_android.utils.IReadMoreWriteLessCacheProperty
import com.au.module_android.utils.ignoreError
import com.au.module_cached.delegate.AppDataStoreStringCache
import com.au.module_gson.fromGson
import com.au.module_gson.toGsonString

/**
 * 通过json string存入到AppDataStoreStringCache。实现转换Json。
 * 不能直接修改你的Class里面的内容是不会给你保存的。 你需要·等于一下·就能保存了。
 *
 * warn：注意目前只传入了一层 class，因此不能支持嵌套泛型。尽量简约。
 */
class AppDataStoreJsonCache<T : Any> (
    key: String,
    defaultValue: T,
    private val clz: Class<T>,
    cacheFileName: String? = null
) : IReadMoreWriteLessCacheProperty<T>(key, defaultValue) {

    private var cache by AppDataStoreStringCache(key, defaultValue.toGsonString(), cacheFileName)
    override fun read(key: String, defaultValue: T): T {
        val jsonStr = cache
        if (jsonStr.isNotEmpty()) {
            return ignoreError { fromGson(jsonStr, clz) } ?: defaultValue
        }
        return defaultValue
    }

    override fun save(key: String, value: T) {
        val c = value.toGsonString()
        cache = c
    }
}