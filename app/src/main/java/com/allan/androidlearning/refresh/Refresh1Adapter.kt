package com.allan.androidlearning.refresh

import android.view.ViewGroup
import com.au.module_nested.recyclerview.viewholder.BindViewHolder
import com.allan.androidlearning.databinding.ItemRefresh1Binding
import com.au.module_nested.recyclerview.DiffCallback
import com.au.module_nested.recyclerview.SmartRLBindRcvAdapter

class Bean(val str:String)

class Refresh1Adapter : SmartRLBindRcvAdapter<Bean, Refresh1ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Refresh1ViewHolder {
        return Refresh1ViewHolder(create(parent))
    }

    override fun isSupportDiffer(): Boolean {
        return true
    }

    override fun createDiffer(a: List<Bean>?, b: List<Bean>?): DiffCallback<Bean> {
        return Differ(a, b)
    }

    class Differ(olds: List<Bean>?, news: List<Bean>?) : DiffCallback<Bean>(olds, news) {
        override fun compareContent(a: Bean, b: Bean): Boolean {
            return a.str == b.str
        }
    }
}

class Refresh1ViewHolder(binding: ItemRefresh1Binding) : BindViewHolder<Bean, ItemRefresh1Binding>(binding) {
    override fun bindData(bean: Bean) {
        super.bindData(bean)
        binding.textTv.text = bean.str
    }
}
