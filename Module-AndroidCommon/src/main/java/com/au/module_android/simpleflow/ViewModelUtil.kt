package com.au.module_android.simpleflow

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 创建状态流
 *
 * @param T 数据类型
 * @param initialValue 初始值，如果为 null，则状态为 Uninitialized
 * @return MutableStateFlow<StatusState<T>> 包含状态信息的可变状态流
 */
fun <T> createStatusStateFlow(
    initialValue: T? = null
): MutableStateFlow<StatusState<T>> {
    return MutableStateFlow(
        if (initialValue != null) {
            StatusState.Success(initialValue)
        } else {
            StatusState.Uninitialized
        }
    )
}

/**
 * 创建共享的状态流（避免初始值问题）
 *
 * @param T 数据类型
 * @param replay 重播次数
 * @return MutableSharedFlow<StatusState<T>> 共享状态流
 */
fun <T> createSharedStatusFlow(
    replay: Int = 0
): MutableSharedFlow<StatusState<T>> {
    return MutableSharedFlow(replay = replay)
}

/**
 * 包装 API 调用，自动处理状态变化
 *
 * @param T 数据类型
 * @param call API 调用的挂起函数
 * @param onState 可选的状态流，用于更新状态
 * @return T? 成功时返回数据，失败时返回 null
 */
suspend fun <T> ViewModel.runCallCatch(
    hasLoading : Boolean = false,
    call: suspend () -> T,
    onState: MutableStateFlow<StatusState<T>>
): T? {
    if(hasLoading) onState.setLoading()
    return try {
        val result = call()
        onState.setSuccess(result)
        result
    } catch (e: Exception) {
        onState.setError(e)
        null
    }
}

//region 设置状态MutableStateFlow<StatusState<T>>的扩展函数

/**
 * 设置成功状态
 *
 * @param T 数据类型
 * @param data 成功数据
 */
fun <T> MutableStateFlow<StatusState<T>>.setSuccess(data: T) {
    value = StatusState.Success(data)
}

/**
 * 设置错误状态
 *
 * @param T 数据类型
 * @param throwable 错误信息
 */
fun <T> MutableStateFlow<StatusState<T>>.setError(throwable: Throwable) {
    value = StatusState.Error(throwable)
}

/**
 * 设置加载状态
 *
 * @param T 数据类型
 */
fun <T> MutableStateFlow<StatusState<T>>.setLoading() {
    value = StatusState.Loading
}

/**
 * 设置未初始化状态
 *
 * @param T 数据类型
 */
fun <T> MutableStateFlow<StatusState<T>>.setUninitialized() {
    value = StatusState.Uninitialized
}
//endregion