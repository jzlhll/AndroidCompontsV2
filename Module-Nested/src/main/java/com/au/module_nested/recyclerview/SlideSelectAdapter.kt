package com.au.module_nested.recyclerview

/** RecyclerView 滑选使用的稳定数据代理。 */
interface SlideSelectAdapter {
    /** 当前可选择的数据数量。 */
    val itemCount: Int

    /** 指定位置是否已选中。 */
    fun isItemSelected(position: Int): Boolean

    /** 设置指定位置的选中状态。 */
    fun setItemSelected(position: Int, selected: Boolean)
}
