package com.au.module_nested.recyclerview

import android.util.Size
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager

/**  按整列宽估算未布局 item 的图片尺寸，不扣除 decoration，预绑定与入屏共用。 */
fun RecyclerView.thumbnailSize(aspectRatio: Float): Size {
    require(aspectRatio > 0f && aspectRatio.isFinite())
    val spanCount = when (val manager = layoutManager) {
        is GridLayoutManager -> manager.spanCount
        is StaggeredGridLayoutManager -> manager.spanCount
        else -> 1
    }
    val itemWidth = (width - paddingLeft - paddingRight) / spanCount
    return Size(itemWidth, (itemWidth / aspectRatio).toInt())
}
