package com.au.module_android.utilthread

import com.au.module_android.utils.launchOnThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

/**
 * 防抖协程请求：每次 [request] 通过 [readDebounceKey] 得到当前逻辑 key，与 [keysEqual] 判定是否同 key。
 * 同 key 且在 [debounceIntervalMs] 内则忽略；key 变化则 [kotlinx.coroutines.Job.cancel] 上一段在飞任务后再启动 [block]。
 */
class DebouncedCoroutineRequestHelper<K>(
    private val scope: CoroutineScope,
    private val debounceIntervalMs: Long,
    private val keysEqual: (K, K) -> Boolean,
) {
    private var activeJob: Job? = null
    private var recordedKey: K? = null
    private var lastDispatchWallClockMs: Long = 0L

    fun request(
        readDebounceKey: () -> K,
        onSkippedWithinDebounce: () -> Unit = {},
        block: suspend CoroutineScope.() -> Unit,
    ) {
        val currentKey = readDebounceKey()
        val now = System.currentTimeMillis()
        val sameKey = recordedKey?.let { keysEqual(it, currentKey) } == true
        if (sameKey) {
            if (now - lastDispatchWallClockMs < debounceIntervalMs) {
                onSkippedWithinDebounce()
                return
            }
        } else {
            activeJob?.cancel()
            recordedKey = currentKey
        }
        lastDispatchWallClockMs = now

        val job = scope.launchOnThread(block)
        activeJob = job
        job.invokeOnCompletion {
            if (activeJob === job) {
                activeJob = null
            }
        }
    }
}