package com.au.module_nested.recyclerview

import androidx.annotation.CallSuper
import androidx.recyclerview.widget.DiffUtil
import com.au.module_android.Globals
import com.au.module_android.utils.launchOnThread
import com.au.module_nested.recyclerview.viewholder.BindViewHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * author: allan
 * 适用于放在SmartRefreshLayout里面
 */
abstract class SmartRLBindRcvAdapter<DATA:Any, VH: BindViewHolder<DATA, *>> :
    BaseAdapter<DATA, VH>(), ILoadMoreAdapter<DATA> {
    open val isReplaceDatas = false
    internal var hasMore = false

    fun setNoMore() {
        hasMore = false
    }

    /**
     * 加载更多数据
     */
    override fun appendDatas(appendList: List<DATA>?, hasMore: Boolean) {
        this.hasMore = hasMore
        appendDatasOnly(appendList)
    }

    protected open fun appendDatasOnly(appendList: List<DATA>?) {
        if (!appendList.isNullOrEmpty()) {
            val realDatas = mutableListOf<DATA>()
            realDatas.addAll(appendList)
            addItems(realDatas)
        }
    }

    protected open fun endInitDatasBlock(oldDataSize: Int, newDataSize: Int) {
        onDataChanged(DataChangeExtraInfoInit(oldDataSize, newDataSize))
    }

    /**
     * 如果是占位图显示；则需要调用initWithPlacesHolder。替换的时候，不能做差异化更新。
     */
    override fun initDatas(datas: List<DATA>?, hasMore: Boolean, isTraditionalUpdate: Boolean) {
        this.hasMore = hasMore

        //必须在前面
        val oldDataSize = this.datas.size
        val newDataSize = datas?.size ?: 0

        val newList = if (datas.isNullOrEmpty()) {
            null
        } else if (datas == this.datas) {
            mutableListOf<DATA>().also { it.addAll(datas) }
        } else {
            datas
        }

        //如果是占位图显示；则需要调用initWithPlacesHolder。
        if (newList == null || !isSupportDiffer() || isPlacesHolder || isTraditionalUpdate) {
            isPlacesHolder = false
            submitTraditional(newList)
            endInitDatasBlock(oldDataSize, newDataSize)
        } else {
            Globals.mainScope.launchOnThread {
                getDiffResultAsync(newList) {
                    endInitDatasBlock(oldDataSize, newDataSize)
                }
            }
        }
    }

    @CallSuper
    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bindData(datas[position])
    }

    /**
     * 当需要进行局部化差异更新的时候，会创建differ。
     */
    protected open fun createDiffer(a:List<DATA>?, b:List<DATA>?): DiffCallback<DATA>? {
        return null
    }

    /**
     * 是否支持差异更新。如果支持修改为true；并实现createDiffer
     */
    protected abstract fun isSupportDiffer():Boolean

    private fun getDiffResultAsync(
        newList: List<DATA>,
        endCallback:()->Unit
    ) {
        Globals.mainScope.launchOnThread {
            val differ = createDiffer(datas, newList)
                ?: throw RuntimeException("BindRcvAdapter: cannot call summitList without implement createDiffer()")

            val result = DiffUtil.calculateDiff(differ, true)

            withContext(Dispatchers.Main) {
                //完事后，再更改本地list
                if (isReplaceDatas && newList is MutableList<DATA>) {
                    datas = newList
                } else {
                    datas.clear()
                    datas.addAll(newList)
                }
                result.dispatchUpdatesTo(this@SmartRLBindRcvAdapter)
                endCallback()
            }
        }
    }
}