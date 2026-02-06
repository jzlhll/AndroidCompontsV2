package com.au.module_android.utilthread

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlin.math.min

/**
 * 用于并发限制且针对同一Key任务合并的执行器
 * 1. 同一个Key的任务，如果前一个还在执行，后一个会等待前一个的结果（任务合并）
 * 2. 限制同时执行的任务数量（并发控制）
 */
class KeyedConcurrentTaskExecutor<T>(
    maxConcurrency: Int = min(4, Runtime.getRuntime().availableProcessors())
) {
    private val mutex = Mutex()
    private val taskMap = mutableMapOf<String, CompletableDeferred<T>>()
    private val semaphore = Semaphore(maxConcurrency)

    suspend fun execute(key: String, block: suspend () -> T): T {
        var isOwner = false
        val deferred = mutex.withLock {
            val existing = taskMap[key]
            if (existing != null) {
                existing
            } else {
                val newDeferred = CompletableDeferred<T>()
                taskMap[key] = newDeferred
                isOwner = true
                newDeferred
            }
        }

        if (!isOwner) {
            return deferred.await()
        }

        return try {
            semaphore.withPermit {
                val result = block()
                deferred.complete(result)
                result
            }
        } catch (e: Exception) {
            deferred.completeExceptionally(e)
            throw e
        } finally {
            mutex.withLock {
                taskMap.remove(key)
            }
        }
    }
}
