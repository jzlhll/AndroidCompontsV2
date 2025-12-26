package com.au.module_android.simplelivedata

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer

interface ReadOnlyMustNoStickLiveData<out T> {
    val value: T?
    val realValue: T?
    fun observeUnStick(owner: LifecycleOwner, observer: Observer<in T>)
    fun observeForeverUnStick(observer: Observer<in T>)
    fun removeObserver(observer: Observer<in T>)
}