package com.au.jobstudy.completed

import android.view.ViewGroup
import com.au.module_nested.recyclerview.AutoLoadMoreBindRcvAdapter
import com.au.module_nested.recyclerview.DiffCallback
import com.au.module_nested.recyclerview.IMultiViewTypeBean
import com.au.module_nested.recyclerview.viewholder.BindViewHolder

class CompletedAdapter(private val itemClick:(CompletedBean)->Unit) : AutoLoadMoreBindRcvAdapter<IMultiViewTypeBean, BindViewHolder<IMultiViewTypeBean, *>>() {
    override fun createDiffer(a: List<IMultiViewTypeBean>?, b: List<IMultiViewTypeBean>?): DiffCallback<IMultiViewTypeBean> {
        return Differ(a, b)
    }

    class Differ(aList:List<IMultiViewTypeBean>?, bList:List<IMultiViewTypeBean>?) : DiffCallback<IMultiViewTypeBean>(aList, bList) {
        override fun compareContent(a: IMultiViewTypeBean, b: IMultiViewTypeBean): Boolean {
            val aIsCompletedBean = a is CompletedBean
            val bIsCompletedBean = b is CompletedBean

            if (aIsCompletedBean && bIsCompletedBean) {
                if (a == b) {
                    return true
                }
                if (a.workEntity.id == b.workEntity.id) {
                    return true
                }
                return false
            } else if (!aIsCompletedBean && !bIsCompletedBean) {
                return (a as CompletedDateBean).day == (b as CompletedDateBean).day && a.isWeek == b.isWeek
            } else {
                return false
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindViewHolder<IMultiViewTypeBean, *> {
        return if(viewType == 1) CompletedViewHolder(itemClick, create(parent))
        else CompletedDateViewHolder(create(parent))
    }
}
