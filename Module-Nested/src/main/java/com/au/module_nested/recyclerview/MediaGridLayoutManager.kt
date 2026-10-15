package com.au.module_nested.recyclerview

import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.OrientationHelper
import androidx.recyclerview.widget.RecyclerView

/**
 * 为竖向正序的固定行高网格登记双向预取，内容项须占一列，Header 可占满行。
 * 创建、绑定及执行预算由 GapWorker 管理；尚无内容尺寸时按方形列宽估算行距。
 */
class MediaGridLayoutManager(
    recyclerView: RecyclerView,
    spanCount: Int,
    val prefetchRows: Int,
    private val isMediaPosition: (Int) -> Boolean = { true },
) : GridLayoutManager(recyclerView.context, spanCount) {
    private val verticalHelper = OrientationHelper.createVerticalHelper(this)

    init {
        require(prefetchRows >= 0)
    }

    override fun collectAdjacentPrefetchPositions(
        dx: Int,
        dy: Int,
        state: RecyclerView.State,
        layoutPrefetchRegistry: RecyclerView.LayoutManager.LayoutPrefetchRegistry,
    ) {
        if (prefetchRows == 0 || dy == 0 || childCount == 0 || state.itemCount == 0) return
        val first = findFirstVisibleItemPosition()
        val last = findLastVisibleItemPosition()
        if (first !in 0 until state.itemCount || last !in first until state.itemCount) return
        val firstView = findViewByPosition(first) ?: return
        val lastView = findViewByPosition(last) ?: return
        var rowStride = 0
        for (index in 0 until childCount) {
            val child = getChildAt(index) ?: continue
            val position = getPosition(child)
            if (position in 0 until state.itemCount && isMediaPosition(position)) {
                rowStride = kotlin.math.max(rowStride, verticalHelper.getDecoratedMeasurement(child))
            }
        }
        // 仅 Header 可见时，按方形媒体尺寸估算首批候选的行距。
        if (rowStride <= 0) rowStride = (width - paddingLeft - paddingRight) / this.spanCount
        if (rowStride <= 0) return
        collectPrefetchSide(
            first - 1, -1, state.itemCount,
            kotlin.math.max(0, verticalHelper.startAfterPadding - verticalHelper.getDecoratedStart(firstView)),
            rowStride, layoutPrefetchRegistry,
        )
        collectPrefetchSide(
            last + 1, 1, state.itemCount,
            kotlin.math.max(0, verticalHelper.getDecoratedEnd(lastView) - verticalHelper.endAfterPadding),
            rowStride, layoutPrefetchRegistry,
        )
    }

    // 媒体项均占一列，Header 不占预取配额；距离逐行递增，避免远处候选绕过帧间预算。
    private fun collectPrefetchSide(
        start: Int,
        step: Int,
        itemCount: Int,
        edgeDistance: Int,
        rowStride: Int,
        registry: RecyclerView.LayoutManager.LayoutPrefetchRegistry,
    ) {
        var count = 0
        var position = start
        while (position in 0 until itemCount && count < prefetchRows * this.spanCount) {
            if (isMediaPosition(position)) {
                val distance = edgeDistance + count / this.spanCount * rowStride
                registry.addPosition(position, distance)
                count++
            }
            position += step
        }
    }
}
