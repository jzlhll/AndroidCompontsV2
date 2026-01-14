package com.au.module_gson.mmkv

import com.au.module_android.utils.IReadMoreWriteLessCacheProperty

/**
 * 通过MMKV存储String列表数据
 */
class MMKVStringListCache(
    key: String,
    defaultValue: ArrayList<String>
) : IReadMoreWriteLessCacheProperty<ArrayList<String>>(key, defaultValue) {

    override fun read(key: String, defaultValue: ArrayList<String>): ArrayList<String> {
        return mmkvGetArrayList<String>(key)
    }

    override fun save(key: String, value: ArrayList<String>) {
        mmkvSetArrayList(key, value)
    }
}