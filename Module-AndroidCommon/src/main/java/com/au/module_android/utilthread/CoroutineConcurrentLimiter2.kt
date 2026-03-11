/* Created on 2026/03/11.
* Copyright (C) 2026 @jzlhll. All rights reserved.
*
* Licensed under the MIT License.
* See LICENSE file in the project root for full license information.
*/

package com.au.module_android.utilthread

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Kotlin协程并发限制 Pro 版 (基于 Semaphore)
 *
 * 相比 [CoroutineConcurrentLimiter] 的改进：
 * 1. 移除了 `submittedJobs` 队列维护，更加轻量，减少内存开销。
 * 2. 提供了 `submitAndWait` 挂起函数，支持在当前协程直接执行（受并发限制），避免创建额外 Job。
 * 3. 灵活性更高：可配合 Channel 实现真正的背压（Backpressure）。
 *
 * 使用建议：
 * - 若需背压（避免瞬间创建大量 Job）：请在循环中直接调用 `submitAndWait`。
 * - 若需异步提交：使用 `submit`，但注意大量调用仍会瞬间创建大量 Job（只是在 Semaphore 处挂起）。

使用示例：
// 在外部创建一个作用域（coroutineScope）
coroutineScope {
    // 假设我们要处理 10000 个文件
    files.forEach { file ->
    launch { // 1. 这里 launch，Job 会自动被 coroutineScope 收集
        limiterPro.submitAndWait { // 2. 这里只负责限流
            process(file)
        }
    }
}
} // 3. 只有当上面所有 launch 的任务都结束后，代码才会继续往下走
println("所有任务完成！")

 */
class CoroutineConcurrentLimiter2(
    private val maxConcurrency: Int,
    private val baseDispatcher: CoroutineDispatcher = Dispatchers.IO,
    // 默认 Scope，用于异步 submit。若外部不传，默认使用 SupervisorJob + IO
    private val scope: CoroutineScope = CoroutineScope(baseDispatcher + SupervisorJob() + CoroutineExceptionHandler { _, _ -> })
) {

    // 核心：使用信号量控制并发
    private val semaphore = Semaphore(maxConcurrency)

    init {
        require(maxConcurrency > 0) { "最大并发数 maxConcurrency 必须大于0，当前值：$maxConcurrency" }
    }

    /**
     * 推荐: 挂起提交任务，在当前协程执行，受并发限制。
     *
     * 特性：
     * 1. 不会创建新 Job（除非调用者自己 launch）。
     * 2. 若并发达到上限，调用者会被挂起（Suspend），直到有空闲资源。
     * 3. 支持返回值。
     *
     * 适用场景：
     * - 在 `repeat` 或 `forEach` 循环中直接调用，可实现天然的背压（Backpressure），
     *   即前一个任务不结束（或并发未满），循环不会继续，从而避免瞬间创建大量任务。
     */
    suspend fun <T> submitAndWait(block: suspend () -> T): T {
        // 使用 withContext 确保任务在指定的 dispatcher 上执行
        // semaphore.withPermit 会在获取锁后执行 block
        return semaphore.withPermit {
            withContext(baseDispatcher) {
                block()
            }
        }
    }

    /**
     * 异步提交任务（类似隔壁的 submit），受并发限制。
     *
     * 注意：
     * - 此方法会立即启动一个新协程（Job），若并发满则在协程内部挂起等待。
     * - 若瞬间调用 1000 次，依然会瞬间创建 1000 个 Job（虽然只有 maxConcurrency 个在运行）。
     * - 若需避免 Job 爆仓，请改用 `submitAndWait` 配合外部控制。
     *
     * @return Job 可单独取消该任务
     */
    fun submit(block: suspend () -> Unit): Job {
        return scope.launch(baseDispatcher) {
            semaphore.withPermit {
                block()
            }
        }
    }

    /**
     * 获取当前允许的剩余并发数（近似值）
     */
    fun getAvailablePermits(): Int {
        return semaphore.availablePermits
    }
}