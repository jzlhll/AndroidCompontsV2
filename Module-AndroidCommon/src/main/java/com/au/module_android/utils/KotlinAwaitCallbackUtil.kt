package com.au.module_android.utils

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/*
// 1. 基本使用
suspend fun resolveServiceWithRetry(serviceInfo: NsdServiceInfo): NsdServiceInfo? {
    return try {
        suspendCallback<NsdServiceInfo> { callback ->
            nsdManager.resolveService(serviceInfo, createResolveListener(callback))
        }
    } catch (e: Exception) {
        loge(TAG, e) { "Failed to resolve service" }
        null
    }
}

// 2. 带取消清理的示例（如果 API 需要取消注册）
suspend fun discoverServices(): List<NsdServiceInfo> = suspendCallback(
    onRegister = { callback ->
        val listener = createDiscoveryListener(callback)
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    },
    onCancel = { callback ->
        nsdManager.stopServiceDiscovery(callback as NsdManager.DiscoveryListener)
    }
)

// 3. 组合多个回调转换
suspend fun performNetworkOperation(): Result {
    val step1 = suspendCallback<Data> { callback ->
        api.operation1(object : ApiCallback<Data> {
            override fun onSuccess(data: Data) = callback.onSuccess(data)
            override fun onFailure(error: ApiError) = callback.onError(error.toException())
        })
    }

    val step2 = suspendCallback<Data> { callback ->
        api.operation2(step1, object : ApiCallback<Data> {
            override fun onSuccess(data: Data) = callback.onSuccess(data)
            override fun onFailure(error: ApiError) = callback.onError(error.toException())
        })
    }

    return Result(step1, step2)
}
*/

suspend fun <T> awaitTimeoutCallback(
    timeoutMillis: Long,
    onRegister: (callback: SuspendCallback<T>) -> Unit,
    onCancel: ((callback: SuspendCallback<T>) -> Unit)? = null
): T? = withTimeoutOrNull(timeoutMillis) {
    awaitCallback(
        onRegister = onRegister,
        onCancel = onCancel
    )
}

/**
 * 将回调式 API 转换为挂起函数的通用封装
 * @param onRegister 注册回调的函数，接收一个回调处理器
 * @param onCancel 可选的取消操作（某些 API 需要手动取消注册）
 */
suspend fun <T> awaitCallback(
    onRegister: (callback: SuspendCallback<T>) -> Unit,
    onCancel: ((callback: SuspendCallback<T>) -> Unit)? = null
) = suspendCancellableCoroutine { cont ->
    val callback = object : SuspendCallback<T> {
        private var completed = false

        override fun onSuccess(value: T) {
            if (completed) return
            completed = true

            if (cont.isActive) {
                cont.resume(value)
            }
        }

        override fun onError(error: Throwable) {
            if (completed) return
            completed = true

            if (cont.isActive) {
                cont.resumeWithException(error)
            }
        }

        override fun onError(code: Int, message: String) {
            if (completed) return
            completed = true

            if (cont.isActive) {
                cont.resumeWithException(Exception("Error $code: $message"))
            }
        }
    }

    try {
        onRegister(callback)
    } catch (e: Exception) {
        if (cont.isActive) {
            cont.resumeWithException(e)
        }
        return@suspendCancellableCoroutine
    }

    // 协程取消时执行清理
    cont.invokeOnCancellation {
        onCancel?.invoke(callback)
    }
}

/**
 * 回调接口，支持多种错误类型
 */
interface SuspendCallback<T> {
    fun onSuccess(value: T)
    fun onError(error: Throwable)
    fun onError(code: Int, message: String) = onError(Exception("Error $code: $message"))
}