package com.au.module_android.utilthread
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SingleCoroutineTaskExecutor(threadName: String = "SingleCoroutineTaskExecutor") {
    // 1. 单线程执行器（守护线程，避免阻塞进程退出）
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, threadName).apply { isDaemon = true }
    }

    // 2. 转换为协程调度器（绑定单线程池）
    private val dispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()

    // 3. 协程作用域（SupervisorJob：子协程失败不影响其他协程）
    private val scope = CoroutineScope(dispatcher + SupervisorJob() + CoroutineExceptionHandler { _, _ ->
        //忽略异常
    })

    // 标记是否已关闭，避免重复操作
    @Volatile
    private var isShutdown = false

    /**
     * 异步提交任务（按添加顺序执行，不等待结果）
     * @param block 挂起函数类型的任务代码块
     */
    fun submit(block: suspend () -> Unit) {
        check(!isShutdown) { "Task executor has been shutdown, cannot submit new tasks" }
        scope.launch {
            block()
        }
    }

    /**
     * 同步等待任务完成（按添加顺序执行，无返回值）
     */
    suspend fun await(block: suspend () -> Unit) {
        check(!isShutdown) { "Task executor has been shutdown, cannot execute await task" }
        val deferred = scope.async {
            block()
        }
        deferred.await()
    }

    /**
     * 同步等待任务完成并获取结果（按添加顺序执行，有返回值）
     */
    suspend fun <T> awaitResult(block: suspend () -> T): T {
        check(!isShutdown) { "Task executor has been shutdown, cannot execute awaitResult task" }
        val deferred = scope.async {
            block()
        }
        return deferred.await()
    }

    /**
     * 优雅关闭：停止接受新任务，等待已提交任务完成（超时后强制终止）
     * @param timeout 等待超时时间
     * @param unit 时间单位
     * @return true=所有任务完成并关闭；false=超时后强制终止
     */
    fun shutdown(timeout: Long = 5, unit: TimeUnit = TimeUnit.SECONDS): Boolean {
        if (isShutdown) return true // 避免重复关闭
        isShutdown = true

        // 1. 取消协程作用域（停止新协程启动，取消未执行的协程）
        scope.cancel()

        // 2. 发起线程池关闭（不再接受新任务）
        executor.shutdown()

        // 3. 等待已提交任务完成，超时则强制终止
        return try {
            executor.awaitTermination(timeout, unit)
        } catch (_: InterruptedException) {
            // 若等待过程被中断，强制关闭线程池
            executor.shutdownNow()
            Thread.currentThread().interrupt() // 恢复中断标记
            false
        }
    }

    /**
     * 强制关闭：立即终止所有正在执行的任务，返回未执行的任务列表
     */
    fun shutdownNow(): List<Runnable> {
        if (isShutdown) return emptyList()
        isShutdown = true

        scope.cancel() // 取消协程
        return executor.shutdownNow() // 强制终止线程池，返回未执行的任务
    }
}

// 测试示例
//fun main() = runBlocking {
//    val executor = SingleCoroutineTaskExecutor("MySingleThread")
//
//    // 1. 异步提交任务
//    executor.submit {
//        delay(100)
//        println("Async task 1 done: ${Thread.currentThread().name}")
//    }
//
//    // 2. 同步等待任务完成
//    executor.await {
//        delay(100)
//        println("Await task done: ${Thread.currentThread().name}")
//    }
//
//    // 3. 同步等待并获取结果
//    val result = executor.awaitResult {
//        delay(100)
//        "Result from awaitResult: ${Thread.currentThread().name}"
//    }
//    println(result)
//
//    // 4. 优雅关闭
//    executor.shutdown()
//    println("Executor shutdown completed")
//}