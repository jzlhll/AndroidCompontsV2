package com.au.module_android.utilthread

import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Kotlin协程并发限制封装类

// 1. 在Activity/Fragment中创建Submitter（绑定生命周期，避免泄漏）
val submitter = LimitedParallelismSubmitter(
    maxConcurrency = 2, // 限制最大并发2个
    coroutineScope = lifecycleScope // 绑定页面生命周期，页面销毁自动取消协程
)

// 2. 提交任务（比如批量下载/接口请求）
fun submitTasks() {
    // 提交5个耗时任务，最多2个同时执行
    repeat(5) { taskId ->
    submitter.submit {
        // 任务运行在子线程（IO调度器）
        println("任务$taskId 开始执行，线程：${Thread.currentThread().name}")
        delay(1000) // 模拟子线程耗时操作（如网络请求、文件读写）
        println("任务$taskId 执行完成")
    }

    //可选：是否等待
    //submitter.joinAll()
}

// 4. 页面销毁/清理时取消所有任务（可选，lifecycleScope会自动处理，但显式调用更安全）
    fun onDestroy() {
        submitter.cancelAll()
    }
 */
class CoroutineConcurrentLimiter(private val maxConcurrency: Int,
                                 private val baseDispatcher: CoroutineDispatcher = Dispatchers.IO,
                                 private val scope: CoroutineScope = CoroutineScope(baseDispatcher + SupervisorJob() + CoroutineExceptionHandler { _, _ ->
                                     //忽略异常
                                 })
) {
    // 核心：基于基础调度器创建“并发受限”的子调度器
    private val limitedDispatcher = baseDispatcher.limitedParallelism(maxConcurrency)

    // 线程安全的Job队列，存储所有已提交的任务（支持多线程提交）
    private val submittedJobs = ConcurrentLinkedQueue<Job>()

    init {
        // 参数校验：最大并发数必须大于0
        require(maxConcurrency > 0) { "最大并发数 maxConcurrency 必须大于0，当前值：$maxConcurrency" }
    }

    /**
     * 提交挂起任务，自动受并发数限制，运行在子线程
     * @param block 要执行的挂起任务
     * @return Job 可单独取消该任务
     */
    fun submit(block: suspend () -> Unit): Job {
        val job = scope.launch(limitedDispatcher) {
            block()
        }
        // 将任务添加到队列，用于后续joinAll/cancelAll
        submittedJobs.add(job)
        // 任务完成后自动从队列移除（可选优化，减少队列冗余）
        job.invokeOnCompletion {
            submittedJobs.remove(job)
        }
        return job
    }

    /**
     * 挂起函数：等待所有已提交的任务执行完成（非阻塞线程）
     * 执行后队列会自动清理已完成的任务
     */
    suspend fun joinAll() {
        // 先拷贝队列避免遍历中修改，再等待所有任务完成
        val jobs = submittedJobs.toSet()
        jobs.joinAll()
        // 清理已完成的任务（双重保障）
        submittedJobs.removeAll(jobs)
    }

    /**
     * 取消所有未完成的任务，清理队列，释放资源
     */
    fun cancelAll() {
        submittedJobs.forEach { job ->
            if (job.isActive) job.cancel()
        }
        submittedJobs.clear()
    }

    /**
     * 获取当前活跃（待执行/执行中）的任务数
     */
    fun getActiveTaskCount(): Int {
        return submittedJobs.count { it.isActive }
    }

    /**
     * 判断是否所有任务都已执行完成
     */
    fun isAllCompleted(): Boolean {
        return submittedJobs.none { it.isActive }
    }
}