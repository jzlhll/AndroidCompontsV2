package com.au.module_androidui.toast

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.view.View
import java.util.ArrayDeque

/**
 * 底部提示的主线程调度器，排队提示在延迟和展示期间阻止后续提示越过。
 */
object ToastBottomMainHandler : Handler(Looper.getMainLooper()) {
    private const val ENQUEUE = 1
    private const val SHOW_NEXT = 2
    private const val FINISHED = 3

    private class Entry(val readyAt: Long, val queued: Boolean, val show: () -> View?)

    private val entries = ArrayDeque<Entry>()
    private var activeToast: Any? = null
    private var activeQueued = false

    /**
     * 立即登记底部提示，readyAt 使用 uptimeMillis 时间基准表示最早展示时间。
     */
    fun enqueue(readyAt: Long, queued: Boolean, show: () -> View?) {
        obtainMessage(ENQUEUE, Entry(readyAt, queued, show)).sendToTarget()
    }

    override fun handleMessage(message: Message) {
        when (message.what) {
            ENQUEUE -> {
                entries.addLast(message.obj as Entry)
                showNext()
            }
            SHOW_NEXT -> showNext()
            FINISHED -> {
                if (message.obj !== activeToast) return
                activeToast = null
                activeQueued = false
                showNext()
            }
        }
    }

    private fun showNext() {
        removeMessages(SHOW_NEXT)
        if (activeQueued) return

        // 首个排队提示等待当前显示和此前已就绪的提示，未到期普通提示不阻挡它。
        val now = SystemClock.uptimeMillis()
        var next: Entry? = null
        for (entry in entries) {
            if (entry.queued) {
                if (activeToast == null &&
                    (next == null || (next.readyAt > now && entry.readyAt < next.readyAt))
                ) {
                    next = entry
                }
                break
            }
            if (next == null || entry.readyAt < next.readyAt) next = entry
        }
        val entry = next ?: return
        if (entry.readyAt > now) {
            sendEmptyMessageAtTime(SHOW_NEXT, entry.readyAt)
            return
        }

        entries.remove(entry)
        val toastView = entry.show()
        if (toastView == null) {
            sendEmptyMessage(SHOW_NEXT)
            return
        }
        val token = Any()
        activeToast = token
        activeQueued = entry.queued
        toastView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit

            override fun onViewDetachedFromWindow(view: View) {
                view.removeOnAttachStateChangeListener(this)
                obtainMessage(FINISHED, token).sendToTarget()
            }
        })
        if (!entry.queued) sendEmptyMessage(SHOW_NEXT)
    }
}
