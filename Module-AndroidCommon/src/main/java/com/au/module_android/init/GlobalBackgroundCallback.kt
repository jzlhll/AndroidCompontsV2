package com.au.module_android.init

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.au.module_android.Globals.activityList
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
        if (isInBackground) {
            isInBackground = false
        }
        notifyListener()
    }

    override fun onStop(owner: LifecycleOwner) {
        //ProcessLifecycleOwner.get().lifecycle监听的结果，onStop就代表进入了后台。如果应用还活着就会回调。
        isInBackground = activityList.isNotEmpty()
        if (isInBackground) {
            notifyListener()
        }
    }

    private fun notifyListener() {
        val inBg = isInBackground
        listeners.forEach {
            it.onBackground(inBg)
        }
    }
}