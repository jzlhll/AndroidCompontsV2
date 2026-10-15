package com.au.module_nested.recyclerview

import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView

/** 将绝对位置转为指定直接子 Adapter 的内容位置，其他子 Adapter 返回 NO_POSITION。 */
fun ConcatAdapter.absoluteToContentPosition(position: Int, contentAdapter: RecyclerView.Adapter<*>): Int {
    if (position !in 0 until itemCount) return RecyclerView.NO_POSITION
    val wrapped = getWrappedAdapterAndPosition(position)
    return if (wrapped.first === contentAdapter) wrapped.second else RecyclerView.NO_POSITION
}

/** 根据当前直接子 Adapter 顺序转换位置，不缓存 Header 数量。 */
fun ConcatAdapter.contentToAbsolutePosition(position: Int, contentAdapter: RecyclerView.Adapter<*>): Int {
    if (position !in 0 until contentAdapter.itemCount) return RecyclerView.NO_POSITION
    var offset = 0
    for (adapter in adapters) {
        if (adapter === contentAdapter) return offset + position
        offset += adapter.itemCount
    }
    return RecyclerView.NO_POSITION
}
