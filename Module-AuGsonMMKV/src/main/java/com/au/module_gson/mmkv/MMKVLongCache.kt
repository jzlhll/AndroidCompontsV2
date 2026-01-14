package com.au.module_gson.mmkv

import com.au.module_android.utils.IReadMoreWriteLessCacheProperty

/**
 * 通过MMKV存储Long类型数据
 */
class MMKVLongCache(
    key: String,
    defaultValue: Long
) : IReadMoreWriteLessCacheProperty<Long>(key, defaultValue) {

    override fun read(key: String, defaultValue: Long): Long {
        return mmkv.getLong(key, defaultValue)
    }

    override fun save(key: String, value: Long) {
        mmkv.putLong(key, value)
    }
}