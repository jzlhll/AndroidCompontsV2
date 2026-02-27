package com.au.jobstudy.star

import android.view.View
import android.view.ViewGroup
import com.au.module_nested.recyclerview.BindRcvAdapter
import com.au.module_nested.recyclerview.viewholder.BindViewHolder
import com.au.jobstudy.MainStarsFragment
import com.au.module_nested.recyclerview.IMultiViewTypeBean

class StarAdapter(val f : MainStarsFragment) : BindRcvAdapter<IMultiViewTypeBean, BindViewHolder<IMultiViewTypeBean, *>>() {
    private val itemBeforeClick: ((View, StarItemBean)->Unit) = { v, bean->
        val rect = intArrayOf(0, 0)
        v.getLocationOnScreen(rect)
        f.binding.dingView.startRunning(rect[0], rect[1])
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindViewHolder<IMultiViewTypeBean, *> {
        if (viewType == VIEW_TYPE_MARKUP) {
            return StarMarkupViewHolder(create(parent))
        }
        if (viewType == VIEW_TYPE_HEAD) {
            return StarHeadViewHolder(create(parent))
        }
        return StarItemViewHolder(create(parent), itemBeforeClick)
    }
}

