package com.au.module_nested.decoration

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * Grid 间距装饰
 * @param spanCount 列数
 * @param spacingHorizontal 横向间距 (px)
 * @param spacingVertical 纵向间距 (px)
 * @param includeEdge 是否包含边缘间距 (如果 RecyclerView 有 padding，通常设为 false)
 */
class GridSpacingItemDecoration(
    private val spanCount: Int,
    private val spacingHorizontal: Int,
    private val spacingVertical: Int,
    private val includeEdge: Boolean = false
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view) // item position
        val column = position % spanCount // item column

        if (includeEdge) {
            outRect.left = spacingHorizontal - column * spacingHorizontal / spanCount
            outRect.right = (column + 1) * spacingHorizontal / spanCount

            if (position < spanCount) { // top edge
                outRect.top = spacingVertical
            }
            outRect.bottom = spacingVertical // item bottom
        } else {
            outRect.left = column * spacingHorizontal / spanCount
            outRect.right = spacingHorizontal - (column + 1) * spacingHorizontal / spanCount
            if (position >= spanCount) {
                outRect.top = spacingVertical // item top
            }
        }
    }
}