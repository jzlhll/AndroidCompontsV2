package com.au.module_android.utilthread

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Kotlin协程并发限制封装类
 * @param maxConcurrency 最大并发数（核心参数）
 * @param coroutineScope 协程作用域，默认使用 SupervisorScope + IO 调度器
 * @param coroutineContext 额外的协程上下文（如调度器、异常处理器）

suspend fun testForeverWait() {
val limiter = CoroutineConcurrentLimiter(maxConcurrency = 3)

// 提交任务
repeat(5) { id ->
limiter.submit {
delay(2000) // 模拟耗时任务
println("任务$id 完成")
}
}

// 永远等待所有任务完成（无超时）
limiter.joinAll()
println("所有任务都完成了，不会触发超时")
}

 */
class CoroutineConcurrentLimiter(
    private val maxConcurrency: Int,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    coroutineContext: CoroutineContext = EmptyCoroutineContext
) {
    // 信号量：控制并发数，permits = 最大并发数
    private val semaphore = Semaphore(maxConcurrency)
    
    // 存储所有提交的任务，用于等待全部完成或取消（线程安全集合）
    private val jobList = mutableListOf<Job>()
    private val listMutex = Mutex()
    
    // 合并后的协程上下文
    private val combinedContext = coroutineScope.coroutineContext + coroutineContext

    /**
     * 提交一个协程任务，自动限制并发数
     * @param block 要执行的挂起任务
     * @return Deferred<T> 可用于获取任务结果/取消任务
     */
    suspend fun <T> submit(block: suspend () -> T): Deferred<T> {
        val deferred = coroutineScope.async(combinedContext) {
            // 获取信号量许可（无许可时挂起等待），执行完自动释放（包括异常场景）
            semaphore.withPermit {
                block()
            }
        }
        
        // 使用 Mutex 保护列表写入，必须在 submit 返回前完成
        listMutex.withLock {
            jobList.add(deferred)
        }
        
        // 任务完成后从列表移除，避免内存泄漏
        deferred.invokeOnCompletion {
            // 这里必须启动新协程，因为 invokeOnCompletion 回调不一定是 suspend 环境，
            // 且我们需要避免阻塞回调线程。
            // 注意：这里仍然存在极小概率的竞态：如果 remove 发生在 add 之前（理论上 async 不会那么快，
            // 但如果 block 为空...）。
            // 不过由于我们上面是先 async 再 add，async 即使立即完成，invokeOnCompletion 也会被调度。
            // 只要 add 是同步等待完成的，逻辑就是安全的。
            coroutineScope.launch {
                listMutex.withLock {
                    jobList.remove(deferred)
                }
            }
        }
        return deferred
    }

    /**
     * 等待所有已提交的任务执行完成（挂起函数）
     * @param timeout 超时时间（可选），超时会抛出TimeoutCancellationException。传入0，就不设置超时时间，默认会等待所有任务完成
     */
    suspend fun joinAll(timeout: Long = 0) {
        // 获取当前任务列表的快照
        val currentTasks = listMutex.withLock {
            if (jobList.isEmpty()) return
            ArrayList(jobList)
        }
        
        if (timeout > 0) {
            withTimeout(timeout) {
                currentTasks.joinAll()
            }
        } else {
            currentTasks.joinAll()
        }
    }

    /**
     * 取消所有未完成的任务
     * @param cause 取消原因（可选）
     */
    suspend fun cancelAll(cause: CancellationException? = null) : Boolean {
        val tasks = listMutex.withLock {
            val copy = ArrayList(jobList)
            jobList.clear()
            copy
        }
        tasks.forEach { it.cancel(cause) }
        return tasks.isNotEmpty()
    }

    /**
     * 获取当前等待/运行中的任务数
     */
    suspend fun getActiveTaskCount(): Int = listMutex.withLock { jobList.size }

    /**
     * 获取当前可用的并发许可数（剩余可同时执行的任务数）
     */
    fun getAvailablePermits(): Int = semaphore.availablePermits

    /**
     * 取消协程作用域，释放所有资源（建议在页面/组件销毁时调用）
     */
    fun release() {
        coroutineScope.cancel()
        // cancelAll() // scope cancel 会自动取消 children，无需手动 cancelAll
    }
}