package com.au.module_android.utilthread

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 使用HandlerThread实现的串行任务执行器。
 * 并且出错以后允许抛回去。
 *
 * @param name 线程名称
 * @param worker 任务处理函数，返回值0表示成功就移除该项任务，如果大于0，就表示postDelay该值，继续重试。
 */
class SerialTaskHandlerExecutor<Bean>(
     private val name:String = "serial_task_handler",
     private val worker: (Bean, retriedCount:Int) -> Long) {
    companion object {
        private const val MSG_ID = 0x1
        private const val MSG_STOP = 0x2
        private const val STOP_DELAY = 30_000L
    }

    private val isRunning = AtomicBoolean(false)

    inner class TaskHandler : Handler {
        constructor(looper: Looper) : super(looper)

        override fun handleMessage(msg: Message) {
            val retriedCount = msg.arg1
            when (msg.what) {
                MSG_ID -> {
                    removeMessages(MSG_STOP)
                    isRunning.set(true)
                    try {
                        val task = msg.obj as Bean
                        val delay = worker(task, retriedCount)
                        if (delay <= 0) {
                            removeMessages(MSG_ID, task)
                        } else {
                            sendMessageDelayed(obtainMessage(MSG_ID,
                                retriedCount + 1, 0, task), delay)
                        }
                    } finally {
                        isRunning.set(false)
                        sendEmptyMessageDelayed(MSG_STOP, STOP_DELAY)
                    }
                }
                MSG_STOP -> {
                    if (!hasPendingTasks()) {
                        clear()
                    }
                }
            }
        }
    }

    private var subHandler : Handler? = null
    private fun getHandler() : Handler {
        val h = subHandler
        if (h != null) {
            return h
        }
        val handleThread = HandlerThread(name + "_" + System.currentTimeMillis())
        handleThread.start()
        val newHandler = TaskHandler(handleThread.looper)
        subHandler = newHandler
        return newHandler
    }

    fun submit(task: Bean) {
        getHandler().apply {
            removeMessages(MSG_STOP)
            val msg = obtainMessage(MSG_ID, 0, 0, task)
            sendMessage(msg)
        }
    }

    fun clear() {
        subHandler?.removeCallbacksAndMessages(null)
        subHandler?.looper?.quit()
        subHandler = null
    }

    /**
     * 判断是否还有任务（正在执行或等待执行）
     */
    fun hasPendingTasks(): Boolean {
        return isRunning.get() || subHandler?.hasMessages(MSG_ID) == true
    }
}