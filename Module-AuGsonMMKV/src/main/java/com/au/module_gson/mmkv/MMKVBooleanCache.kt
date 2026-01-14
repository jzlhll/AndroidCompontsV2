package com.au.module_gson.mmkv

import com.au.module_android.utils.IReadMoreWriteLessCacheProperty

/**
 * 通过MMKV存储Boolean类型数据
 */
class MMKVBooleanCache(
    key: String,
    defaultValue: Boolean
) : IReadMoreWriteLessCacheProperty<Boolean>(key, defaultValue) {

    override fun read(key: String, defaultValue: Boolean): Boolean {
        return mmkv.getBoolean(key, defaultValue)
    }

    override fun save(key: String, value: Boolean) {
        mmkv.putBoolean(key, value)
    }
}