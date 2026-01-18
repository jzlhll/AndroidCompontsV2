package com.au.module_android.simpleflow

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * 创建一个不粘滞的Flow。类似[com.au.module_android.simplelivedata.NoStickLiveData]
 * @param T Flow的元素类型
 * @return 可变Flow + 只读Flow
 */
fun <T> createNoStickyFlow(): MutableSharedFlow<T> {
    return MutableSharedFlow(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
}

/**
 * 创建一个粘滞的Flow。类似LiveData
 * @param T Flow的元素类型
 * @return 可变Flow + 只读Flow
 */
fun <T> createStickyFlow(): MutableSharedFlow<T> {
    return MutableSharedFlow(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
}