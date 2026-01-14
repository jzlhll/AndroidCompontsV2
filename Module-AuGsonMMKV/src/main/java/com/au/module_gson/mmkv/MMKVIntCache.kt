package com.au.module_gson.mmkv

import com.au.module_android.utils.IReadMoreWriteLessCacheProperty

/**
 * 通过MMKV存储Int类型数据
 */
class MMKVIntCache(
    key: String,
    defaultValue: Int
) : IReadMoreWriteLessCacheProperty<Int>(key, defaultValue) {

    override fun read(key: String, defaultValue: Int): Int {
        return mmkv.getInt(key, defaultValue)
    }

    override fun save(key: String, value: Int) {
        mmkv.putInt(key, value)
    }
}