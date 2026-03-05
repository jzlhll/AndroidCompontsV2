package com.au.module_nested.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View

/**
 * 专门用于在 NestedScrollView 中完全展开的 RecyclerView。
 * 注意：仅适用于 item 数量较少的情况，因为会一次性测量所有 item，失去复用优势。
 */
class FullHeightRecyclerView : NoTopEffectRecyclerView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val expandSpec = View.MeasureSpec.makeMeasureSpec(Int.MAX_VALUE shr 2, View.MeasureSpec.AT_MOST)
        super.onMeasure(widthSpec, expandSpec)
    }
}
