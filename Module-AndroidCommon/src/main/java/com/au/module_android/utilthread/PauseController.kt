//  Created on 2026/03/02.
// Copyright (C) 2026 @jzlhll. All rights reserved.
//
// Licensed under the MIT License.
// See LICENSE file in the project root for full license information.

package com.au.module_android.utilthread

import kotlinx.coroutines.CompletableDeferred

/**
 * 协程暂停控制器。
 * 用于在协程执行过程中实现暂停和恢复逻辑。
 * 线程安全。
 */
class PauseController {
    companion object {
        const val PAUSE_CODE = 1
        const val STOP_CODE = 2
        const val DEFAULT_WORKING_CODE = 0
    }

    private val lock = Any()
    private var pauseDeferred: CompletableDeferred<Int>? = null

    /**
     * 当前是否处于暂停状态
     */
    val isPaused: Boolean
        get() = synchronized(lock) { pauseDeferred != null }

    /**
     * 暂停执行。
     * 调用此方法后，调用 [waitIfPaused] 的协程将会挂起，直到调用 [resume]。
     * 如果已经处于暂停状态，此调用无效果。
     */
    fun pause() {
        synchronized(lock) {
            if (pauseDeferred == null) {
                pauseDeferred = CompletableDeferred()
            }
        }
    }

    /**
     * 恢复执行。
     * 唤醒所有在 [waitIfPaused] 上挂起的协程。
     * 如果当前未暂停，此调用无效果。
     */
    fun resume() {
        synchronized(lock) {
            pauseDeferred?.complete(PAUSE_CODE)
            pauseDeferred = null
        }
    }

    /**
     * 恢复执行，只是返回参数不同
     * 注意：如果当前未暂停，调用此方法无效果。
     */
    fun stop() {
        synchronized(lock) {
            pauseDeferred?.complete(STOP_CODE)
            pauseDeferred = null
        }
    }

    /**
     * 检查并等待暂停。
     * 如果当前处于暂停状态，当前协程将挂起，直到 [resume] 被调用。
     * 如果未暂停，立即返回。
     */
    suspend fun waitIfPaused() : Int{
        val deferred = synchronized(lock) { pauseDeferred }
        return deferred?.await() ?: DEFAULT_WORKING_CODE
    }
}
