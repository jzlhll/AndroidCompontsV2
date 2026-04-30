package com.au.module_android.utils

import android.view.MotionEvent
import android.view.View

/**
 * 为 View 提供单击触发和长按连续触发能力。
 */
class LongPressRepeatClickHelper(
    private val repeatIntervalMs: Long = 60L,
) {
    private val repeatActions = mutableMapOf<View, Runnable>()

    /**
     * 绑定按钮动作，单击执行一次，长按时按固定间隔连续执行。
     *
     * @param target 需要绑定的按钮或可点击 View
     * @param action 每次触发时执行的动作
     */
    fun bind(target: View, action: () -> Unit) {
        target.setOnClickListener {
            action()
        }
        target.setOnLongClickListener {
            startRepeat(target, action)
            true
        }
        target.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                stopRepeat(view)
            }
            false
        }
    }

    /**
     * 停止所有长按连发任务。
     */
    fun release() {
        repeatActions.keys.toList().forEach { view ->
            stopRepeat(view)
        }
    }

    // 长按触发后立即执行一次，随后按固定间隔重复执行。
    private fun startRepeat(target: View, action: () -> Unit) {
        stopRepeat(target)
        val repeatAction = object : Runnable {
            override fun run() {
                if (!target.isPressed || !target.isEnabled) {
                    stopRepeat(target)
                    return
                }
                action()
                target.postDelayed(this, repeatIntervalMs)
            }
        }
        repeatActions[target] = repeatAction
        target.post(repeatAction)
    }

    // 手指抬起或取消触摸时停止当前 View 的连发。
    private fun stopRepeat(target: View) {
        val repeatAction = repeatActions.remove(target) ?: return
        target.removeCallbacks(repeatAction)
    }
}
