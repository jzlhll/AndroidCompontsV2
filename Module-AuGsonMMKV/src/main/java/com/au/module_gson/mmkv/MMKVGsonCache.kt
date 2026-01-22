package com.au.module_gson.mmkv

import com.au.module_android.utils.IReadMoreWriteLessCacheProperty
import com.au.module_android.utils.ignoreError
import com.au.module_gson.fromGson
import com.au.module_gson.toGsonString
import java.lang.reflect.Type

/**
 * 通过json string存入到MMKV。实现转换Json。
 * 不能直接修改你的Class里面的内容是不会给你保存的。 你需要·等于一下·就能保存了。
 *
 * @param typeToken 参考 [fromGson] 的描述
 */
class MMKVGsonCache<T : Any> (
    key: String,
    defaultValue: T,
    private val typeToken: Type
) : IReadMoreWriteLessCacheProperty<T>(key, defaultValue) {

    override fun read(key: String, defaultValue: T): T {
        val jsonStr = mmkv.getString(key, "")
        if (!jsonStr.isNullOrEmpty()) {
            return ignoreError { fromGson(jsonStr, typeToken) } ?: defaultValue
        }
        return defaultValue
    }

    override fun save(key: String, value: T) {
        val c = value.toGsonString()
        mmkv.putString(key, c)
    }
}