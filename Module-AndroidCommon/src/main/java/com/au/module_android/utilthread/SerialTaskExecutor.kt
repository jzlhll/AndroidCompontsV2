package com.au.module_android.utilthread

import com.au.module_android.Globals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 串行任务执行器
 * 将并发进来的任务转化为串行执行，确保同一时间只有一个任务在处理
 * 其中 Bean 代表的要处理的数据类型。
 */
class SerialTaskExecutor<Bean : Any>(
    private val scope: CoroutineScope = Globals.backgroundScope,
    private val worker: suspend (Bean) -> Unit
) {
    private val mQueue = LinkedBlockingQueue<Bean>()
    private val mIsRunning = AtomicBoolean(false)

    /**
     * 提交任务到队列
     */
    fun submit(task: Bean) {
        mQueue.offer(task)
        processQueue()
    }

    private fun processQueue() {
        if (!mIsRunning.compareAndSet(false, true)) return

        scope.launch {
            try {
                while (true) {
                    val nextTask = mQueue.poll() ?: break
                    worker(nextTask)
                }
            } finally {
                mIsRunning.set(false)
                // 再次检查队列，防止在切换状态瞬间有新任务进入
                if (mQueue.isNotEmpty()) {
                    processQueue()
                }
            }
        }
    }

    /**
     * 清空待处理任务
     */
    fun clear() {
        mQueue.clear()
    }
}