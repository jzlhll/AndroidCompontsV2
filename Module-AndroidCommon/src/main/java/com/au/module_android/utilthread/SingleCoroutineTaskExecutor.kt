package com.au.module_android.utilthread
import kotlinx.coroutines.*

/**
 * 单线程协程任务执行器
 * 1. 按添加顺序执行任务（串行执行）
 * 2. 支持异步提交任务（不阻塞当前线程）
 * 3. 支持同步等待任务完成（阻塞当前线程）
 * 4. 支持取消所有未执行任务（取消所有未开始的协程）
 */
class SingleCoroutineTaskExecutor(
    private val baseDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val scope: CoroutineScope = CoroutineScope(baseDispatcher + SupervisorJob() + CoroutineExceptionHandler { _, _ ->
        //忽略异常
    })
) {
    // 2. 转换为协程调度器（绑定单线程池）
    private val dispatcher: CoroutineDispatcher = baseDispatcher.limitedParallelism(1)

    /**
     * 异步提交任务（按添加顺序执行，不等待结果）
     * @param block 挂起函数类型的任务代码块
     */
    fun submit(block: suspend () -> Unit) {
        scope.launch(dispatcher) {
            block()
        }
    }

    /**
     * 同步等待任务完成（按添加顺序执行，无返回值）
     */
    suspend fun await(block: suspend () -> Unit) {
        val deferred = scope.async(dispatcher) {
            block()
        }
        deferred.await()
    }

    /**
     * 同步等待任务完成并获取结果（按添加顺序执行，有返回值）
     */
    suspend fun <T> awaitResult(block: suspend () -> T): T {
        val deferred = scope.async(dispatcher) {
            block()
        }
        return deferred.await()
    }

    fun close() {
        // 1. 取消协程作用域（停止新协程启动，取消未执行的协程）
        scope.cancel()
    }
}