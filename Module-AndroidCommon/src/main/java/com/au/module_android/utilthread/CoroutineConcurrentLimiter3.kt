/* Created on 2026/03/11.
* Copyright (C) 2026 @jzlhll. All rights reserved.
*
* Licensed under the MIT License.
* See LICENSE file in the project root for full license information.
*/
package com.au.module_android.utilthread

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Kotlin协程并发限制封装类 V3 (队列驱动版)
 *
 * 特性：
 * 1. **泛型支持**：通过构造函数传入具体的任务执行逻辑 `work`。
 * 2. **列表提交**：通过 `submitList` 批量提交数据。
 * 3. **可控停止**：支持 `stopAll(ifStopCurrent)` 清理队列并选择性取消正在执行的任务。
 * 4. **背压与内存优化**：内部使用 Channel 或 队列 管理待执行数据，避免瞬间创建大量 Job。
 *
 * @param T 任务数据类型
 * @param maxConcurrency 最大并发数
 * @param baseDispatcher 协程调度器
 * @param work 具体的任务执行逻辑
 */
class CoroutineConcurrentLimiter3<T>(
    private val maxConcurrency: Int,
    private val baseDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val work: suspend (T) -> Unit
) {
    // 任务Scope，用于启动Worker协程
    private val scope = CoroutineScope(baseDispatcher + SupervisorJob() + CoroutineExceptionHandler { _, _ -> })
    
    // 待执行的数据队列（线程安全）
    private val pendingQueue = ConcurrentLinkedQueue<T>()
    
    // 正在执行的任务Job集合（用于 stopAll 时取消正在运行的任务）
    private val runningJobs = ConcurrentLinkedQueue<Job>()

    // 信号量控制并发
    private val semaphore = Semaphore(maxConcurrency)

    // 标记是否正在处理队列，防止重复启动 loop
    private val isLoopRunning = AtomicBoolean(false)

    init {
        require(maxConcurrency > 0) { "最大并发数 maxConcurrency 必须大于0，当前值：$maxConcurrency" }
    }

    /**
     * 提交任务列表
     * @param list 待处理的数据列表
     */
    fun submitList(list: List<T>) {
        if (list.isEmpty()) return
        
        // 1. 将数据加入队列
        pendingQueue.addAll(list)
        
        // 2. 尝试启动处理循环
        ensureLoopRunning()
    }

    /**
     * 提交单个任务
     */
    fun submit(item: T) {
        pendingQueue.add(item)
        ensureLoopRunning()
    }

    /**
     * 停止所有任务
     * @param ifStopCurrent true: 取消正在执行的任务; false: 仅清空等待队列，让正在执行的任务跑完
     */
    fun stopAll(ifStopCurrent: Boolean) {
        // 1. 清空等待队列
        pendingQueue.clear()
        
        // 2. 如果需要，取消正在执行的任务
        if (ifStopCurrent) {
            val iterator = runningJobs.iterator()
            while (iterator.hasNext()) {
                val job = iterator.next()
                if (job.isActive) {
                    job.cancel()
                }
                iterator.remove()
            }
        }
    }

    /**
     * 获取当前等待队列的大小
     */
    fun getPendingCount(): Int {
        return pendingQueue.size
    }

    /**
     * 获取当前正在执行的任务数
     */
    fun getRunningCount(): Int {
        return runningJobs.size
    }

    private fun ensureLoopRunning() {
        // CAS 操作确保只有一个 loop 协程在运行
        if (isLoopRunning.compareAndSet(false, true)) {
            scope.launch {
                try {
                    while (isActive) {
                        // 1. 获取数据前，先检查队列是否为空，避免不必要的 acquire 阻塞
                        if (pendingQueue.isEmpty()) {
                            break
                        }

                        // 2. 申请许可（控制并发数）。如果当前运行数 >= maxConcurrency，这里会挂起等待
                        semaphore.acquire()

                        // 3. 拿到许可后，再次取数据（Double Check）
                        val item = pendingQueue.poll()
                        if (item == null) {
                            // 队列空了，释放刚才拿到的许可，并退出循环
                            semaphore.release()
                            break
                        }

                        // 4. 启动子协程执行任务
                        val job = launch(baseDispatcher) {
                            try {
                                work(item)
                            } finally {
                                // 任务结束（无论成功失败），释放信号量，让出位置给下一个任务
                                semaphore.release()
                                // 从运行集合中移除自己
                                runningJobs.remove(coroutineContext[Job])
                                // 任务结束时，尝试触发后续任务
                                ensureLoopRunning()
                            }
                        }
                        
                        // 记录 Job 以便 stopAll 使用
                        runningJobs.add(job)
                    }
                } finally {
                    isLoopRunning.set(false)
                    // Double check: 退出前如果队列又有新数据了，递归重启 loop
                    if (!pendingQueue.isEmpty()) {
                        ensureLoopRunning()
                    }
                }
            }
        }
    }
}

/**
 * 本地测试入口 (可以直接运行此 main 函数)
 */
/*
suspend fun test() = runBlocking {
    println("=== 开始测试 CoroutineConcurrentLimiter3 ===")

    // 1. 创建限流器，最大并发 3
    val limiter = CoroutineConcurrentLimiter3<Int>(
        maxConcurrency = 3,
        baseDispatcher = Dispatchers.Default // 测试用 Default
    ) { taskId ->
        // 模拟耗时任务
        println("Task $taskId START at ${Thread.currentThread().name}")
        delay(100) // 模拟 100ms 耗时
        println("Task $taskId END")
    }

    // 2. 批量提交 20 个任务
    println("\n--- 提交 20 个任务 ---")
    val tasks = (1..20).toList()
    limiter.submitList(tasks)

    // 观察运行状态
    repeat(5) {
        delay(50)
        println("Status: Pending=${limiter.getPendingCount()}, Running=${limiter.getRunningCount()}")
    }

    // 3. 追加任务
    println("\n--- 追加单个任务 999 ---")
    limiter.submit(999)

    delay(1000) // 等待一部分任务跑完

    // 4. 测试 StopAll (优雅停止)
    println("\n--- 测试 StopAll(false) - 清空队列但不杀当前任务 ---")
    limiter.submitList((100..110).toList()) // 再加点任务
    println("Before stop: Pending=${limiter.getPendingCount()}")
    limiter.stopAll(ifStopCurrent = false)
    println("After stop: Pending=${limiter.getPendingCount()} (Should be 0)")
    
    delay(500) // 观察是否有任务还在跑（应该只有 stop 前已经开始的在跑）

    // 5. 测试 StopAll (强制停止)
    println("\n--- 测试 StopAll(true) - 强制杀所有 ---")
    limiter.submitList((200..210).toList())
    delay(50) // 让一些跑起来
    println("Running before kill: ${limiter.getRunningCount()}")
    limiter.stopAll(ifStopCurrent = true)
    delay(50)
    println("Running after kill: ${limiter.getRunningCount()} (Should be 0)")

    println("\n=== 测试结束 ===")
}
 */