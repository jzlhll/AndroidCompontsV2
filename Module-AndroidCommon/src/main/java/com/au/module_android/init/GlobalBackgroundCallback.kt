package com.au.module_android.init

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.CopyOnWriteArrayList

object GlobalBackgroundCallback : DefaultLifecycleObserver {
    interface IBackgroundListener {
        fun onBackground(isBackground: Boolean)
    }

    /**
     * 是否是在前台
     */
    val isForeground: Boolean
        get() = !isInBackground

    /**
     * 是否在后台
     */
    val isBackground:Boolean
        get() = isInBackground

    /**
     * 如果是后台启动，是不会触发onStart的。或者可能触发onStop。所以默认值给成是后台状态。
     */
    private var isInBackground = true

    private val listeners by lazy {
        CopyOnWriteArrayList<IBackgroundListener>()
    }

    fun addListener(callback:IBackgroundListener) {
        if(!listeners.contains(callback)) listeners.add(callback)
    }

    fun removeListener(callback:IBackgroundListener) {
        listeners.remove(callback)
    }

    override fun onStart(owner: LifecycleOwner) {
        if (!isInBackground) return
        isInBackground = false
        notifyListener()
    }

    override fun onStop(owner: LifecycleOwner) {
        // 进程生命周期已经代表可见性，不再依赖可能已清空的 Activity 列表。
        if (isInBackground) return
        isInBackground = true
        notifyListener()
    }

    private fun notifyListener() {
        val inBg = isInBackground
        listeners.forEach {
            it.onBackground(inBg)
        }
    }
}
