package com.au.module_gson.mmkv

import com.au.module_android.utils.IReadMoreWriteLessCacheProperty

class MMKVStringCache(key:String, defaultValue:String)
        : IReadMoreWriteLessCacheProperty<String>(key, defaultValue) {
    override fun read(key: String, defaultValue: String): String {
        return mmkv.getString(key, defaultValue) ?: defaultValue
    }

    override fun save(key: String, value: String) {
        mmkv.putString(key, value)
    }
}