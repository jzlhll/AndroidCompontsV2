package com.au.module_android.simplelivedata

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer

interface ReadOnlyNoStickLiveData<out T> : ReadOnlyMustNoStickLiveData<T> {
    fun observe(owner: LifecycleOwner, observer: Observer<in T>)
    fun observeForever(observer: Observer<in T>)
}