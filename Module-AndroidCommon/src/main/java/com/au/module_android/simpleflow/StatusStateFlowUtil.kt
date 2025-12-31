package com.au.module_android.simpleflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * 过滤成功状态的数据
 *
 * @param T 数据类型
 * @return Flow<T> 成功状态下的数据流
 */
fun <T> Flow<StatusState<T>>.filterSuccess(): Flow<T> {
    return filter { it.isSuccess }
        .map { (it as StatusState.Success<T>).data }
}

/**
 * 过滤错误状态的异常
 *
 * @param T 数据类型
 * @return Flow<Throwable> 错误状态下的异常流
 */
fun <T> Flow<StatusState<T>>.filterError(): Flow<Throwable> {
    return filter { it.isError }
        .map { (it as StatusState.Error).throwable }
}

/**
 * 过滤加载状态
 *
 * @param T 数据类型
 * @return Flow<Unit> 加载状态流
 */
fun <T> Flow<StatusState<T>>.filterLoading(): Flow<Unit> {
    return filter { it.isLoading }
        .map { }
}

/**
 * 过滤未初始化状态
 *
 * @param T 数据类型
 * @return Flow<Unit> 未初始化状态流
 */
fun <T> Flow<StatusState<T>>.filterUninitialized(): Flow<Unit> {
    return filter { it.isUninitialized }
        .map { }
}

/**
 * 一次性订阅多个状态的组合函数
 * 收集状态流并根据状态执行相应操作
 *
 * @param T 数据类型
 * @param onUninitialized 未初始化状态的回调
 * @param onLoading 加载状态的回调
 * @param onSuccess 成功状态的回调
 * @param onError 错误状态的回调
 */
suspend fun <T> Flow<StatusState<T>>.collectStatusState(
    onUninitialized: (() -> Unit)? = null,
    onLoading: (() -> Unit)? = null,
    onSuccess: ((T) -> Unit)? = null,
    onError: ((Throwable) -> Unit)? = null
) {
    collect {
        when (it) {
            is StatusState.Uninitialized -> onUninitialized?.invoke()
            is StatusState.Loading -> onLoading?.invoke()
            is StatusState.Success -> onSuccess?.invoke(it.data)
            is StatusState.Error -> onError?.invoke(it.throwable)
        }
    }
}

/**
 * 普通数据流转为状态流
 *
 * @param T 数据类型
 * @param initialValue 初始状态
 * @return Flow<StatusState<T>> 状态流
 */
fun <T> Flow<T>.asStatusState(
    initialValue: StatusState<T> = StatusState.Uninitialized
): Flow<StatusState<T>> {
    return map<T, StatusState<T>> { StatusState.Success(it) }
        .onStart { emit(initialValue) }
        .catch { emit(StatusState.Error(it)) }
}
