package com.au.module_nested.recyclerview

import androidx.recyclerview.widget.DiffUtil

/**
 * author: allan
 * Time: 2022/11/23
 * Desc: 框架设计，兼容外部可空列表入参，子类只处理非空item比较。
 */
abstract class DiffCallback<T : Any>(olds: List<T>?, news: List<T>?) : DiffUtil.Callback() {
    private val oldList: List<T> = olds ?: emptyList()
    private val newList: List<T> = news ?: emptyList()

    final override fun getNewListSize(): Int {
        return newList.size
    }

    final override fun getOldListSize(): Int {
        return oldList.size
    }

    final override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldData = oldList[oldItemPosition]
        val newData = newList[newItemPosition]
        return compareContent(oldData, newData)
    }

    final override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldData = oldList[oldItemPosition]
        val newData = newList[newItemPosition]
        return compareItem(oldData, newData)
    }

    /**
     * 子类实现：用于DiffUtil.Callback计算areItemsTheSame，已经确定，同时不为null。
     * 这里必须比较业务唯一标识，不能只比较class类型。
     */
    abstract fun compareItem(a: T, b: T): Boolean

    /**
     * 子类实现：用于DiffUtil.Callback计算areContentsTheSame，已经确定，同时不为null。
     * 我们需要比较内容是否有变化。
     * 这里有争议的点是，a == b是否return true，场景是前后adapter提交的list，某些item bean是没有变地址的（直接修改了item对象）。
     * 所以，如果你的提交list很确定，每次来的item list都是新创建出来的则可以使用 a == b来加快比较。
     * 而如果是有后续点击，切换等操作，可能导致源数据的变化而更新recyclerView，则不应该判断a == b.
     */
    abstract fun compareContent(a:T, b:T):Boolean
}
