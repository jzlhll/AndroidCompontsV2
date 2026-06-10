package com.au.module_android.utils

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.milliseconds

suspend fun <T> awaitTimeoutCallback(
    timeoutMillis: Long,
    onRegister: (callback: SuspendCallback<T>) -> Unit,
    onCancel: ((callback: SuspendCallback<T>) -> Unit)? = null
): T? = withTimeoutOrNull(timeoutMillis.milliseconds) {
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

/**
 * 支持挂起函数注册和取消清理的回调转换封装
 * 适用于：
 * 1. 注册/创建函数本身是挂起函数（suspend）
 * 2. 注册/创建函数返回一个句柄（Handle），且在取消/超时时需要对该句柄进行清理（如 disconnect/close）
 *
 * @param onRegister 挂起注册函数，返回一个句柄 R
 * @param onCancel 取消或超时时的清理回调，接收句柄 R
 */
suspend fun <T, R> awaitSuspendCallback(
    onRegister: suspend (callback: SuspendCallback<T>) -> R,
    onCancel: ((handle: R) -> Unit)? = null
): T {
    val deferred = CompletableDeferred<T>()
    val callback = object : SuspendCallback<T> {
        override fun onSuccess(value: T) {
            deferred.complete(value)
        }

        override fun onError(error: Throwable) {
            deferred.completeExceptionally(error)
        }
    }

    val handle = onRegister(callback)
    return try {
        deferred.await()
    } finally {
        if (!deferred.isCompleted) {
            onCancel?.invoke(handle)
        }
    }
}

/**
 * 支持挂起函数注册和取消清理的回调转换封装（支持注册函数返回 null 的情况）
 * 如果注册函数返回 null，则直接返回 null，不进行后续的 await 等待。
 *
 * @param onRegister 挂起注册函数，返回一个可空句柄 R?
 * @param onCancel 取消或超时时的清理回调，接收非空句柄 R
 */
suspend fun <T, R : Any> awaitSuspendCallbackOrNull(
    onRegister: suspend (callback: SuspendCallback<T>) -> R?,
    onCancel: ((handle: R) -> Unit)? = null
): T? {
    val deferred = CompletableDeferred<T>()
    val callback = object : SuspendCallback<T> {
        override fun onSuccess(value: T) {
            deferred.complete(value)
        }

        override fun onError(error: Throwable) {
            deferred.completeExceptionally(error)
        }
    }

    val handle = onRegister(callback) ?: return null
    return try {
        deferred.await()
    } finally {
        if (!deferred.isCompleted) {
            onCancel?.invoke(handle)
        }
    }
}

/**
 * 支持挂起函数注册、取消清理和超时控制的回调转换封装
 *
 * @param timeoutMillis 超时时间（毫秒）
 * @param onRegister 挂起注册函数，返回一个句柄 R
 * @param onCancel 取消或超时时的清理回调，接收句柄 R
 */
suspend fun <T, R> awaitTimeoutSuspendCallback(
    timeoutMillis: Long,
    onRegister: suspend (callback: SuspendCallback<T>) -> R,
    onCancel: ((handle: R) -> Unit)? = null
): T? = withTimeoutOrNull(timeoutMillis.milliseconds) {
    awaitSuspendCallback(
        onRegister = onRegister,
        onCancel = onCancel
    )
}

/**
 * 支持挂起函数注册、取消清理和超时控制的回调转换封装（支持注册函数返回 null 的情况）
 * 如果注册函数返回 null，则直接返回 null，不进行后续的 await 等待。
 *
 * @param timeoutMillis 超时时间（毫秒）
 * @param onRegister 挂起注册函数，返回一个可空句柄 R?
 * @param onCancel 取消或超时时的清理回调，接收非空句柄 R
 */
suspend fun <T, R : Any> awaitTimeoutSuspendCallbackOrNull(
    timeoutMillis: Long,
    onRegister: suspend (callback: SuspendCallback<T>) -> R?,
    onCancel: ((handle: R) -> Unit)? = null
): T? = withTimeoutOrNull(timeoutMillis.milliseconds) {
    awaitSuspendCallbackOrNull(
        onRegister = onRegister,
        onCancel = onCancel
    )
}
