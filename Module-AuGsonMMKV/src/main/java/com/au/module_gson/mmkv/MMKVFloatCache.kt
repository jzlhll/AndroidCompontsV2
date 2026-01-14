package com.au.module_gson.mmkv

import com.au.module_android.utils.IReadMoreWriteLessCacheProperty

/**
 * 通过MMKV存储Float类型数据
 */
class MMKVFloatCache(
    key: String,
    defaultValue: Float
) : IReadMoreWriteLessCacheProperty<Float>(key, defaultValue) {

    override fun read(key: String, defaultValue: Float): Float {
        return mmkv.getFloat(key, defaultValue)
    }

    override fun save(key: String, value: Float) {
        mmkv.putFloat(key, value)
    }
}