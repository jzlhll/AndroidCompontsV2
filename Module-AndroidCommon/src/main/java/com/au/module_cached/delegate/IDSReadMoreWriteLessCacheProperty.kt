package com.au.module_cached.delegate

import com.au.module_android.utils.IReadMoreWriteLessCacheProperty

abstract class IDSReadMoreWriteLessCacheProperty<T:Any>(
    key: String,
    defaultValue: T) : IReadMoreWriteLessCacheProperty<T>(key, defaultValue) {

    abstract suspend fun readSuspend(key: String, defaultValue: T) : T
}