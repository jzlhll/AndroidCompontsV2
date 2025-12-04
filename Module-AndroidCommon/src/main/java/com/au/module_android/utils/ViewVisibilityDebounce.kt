package com.au.module_android.utils

import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ViewVisibilityDebounce(private val scope: CoroutineScope,
                             private val view: View) {
    private var debounceJob: Job? = null

    /**
     * 延迟时间（毫秒）
     */
    var delayMillis: Long = 300

    /**
     * 设置view为VISIBLE状态，带防抖功能
     */
    fun visible() {
        debounce {
            view.visibility = View.VISIBLE
        }
    }

    /**
     * 设置view为INVISIBLE状态，带防抖功能
     */
    fun invisible() {
        debounce {
            view.visibility = View.INVISIBLE
        }
    }

    /**
     * 设置view为GONE状态，带防抖功能
     */
    fun gone() {
        debounce {
            view.visibility = View.GONE
        }
    }

    /**
     * 防抖核心逻辑
     */
    private fun debounce(action: () -> Unit) {
        // 取消之前的任务
        debounceJob?.cancel()

        // 创建新任务
        debounceJob = scope.launch {
            delay(delayMillis)
            action()
        }
    }
}
