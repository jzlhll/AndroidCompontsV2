package com.au.module_android.utilthread

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import java.util.concurrent.ConcurrentHashMap

/**
 * 合并相同 key 的并发 IO 请求，完成后自动释放。
 *
 * 同 key 并发调用 [await] 时，只有第一个请求真正执行，其余调用方共享同一个 [Deferred] 结果；
 * 请求完成（成功或失败）后自动从内部表移除，下一次调用会发起新请求。
 */
class KeyedRequestSingleFlight<K, T>(
    private val scope: CoroutineScope,
) {
    private val requests = ConcurrentHashMap<K, Deferred<T>>()

    suspend fun await(key: K, block: suspend () -> T): T {
        val candidate = scope.async(Dispatchers.IO, CoroutineStart.LAZY) { block() }
        val request = requests.putIfAbsent(key, candidate) ?: candidate
        if (request === candidate) {
            candidate.invokeOnCompletion { requests.remove(key, candidate) }
        } else {
            candidate.cancel()
        }
        request.start()
        return request.await()
    }
}
