package com.au.module_nested.recyclerview

import androidx.annotation.CallSuper
import com.au.module_nested.recyclerview.viewholder.BindViewHolder
import com.scwang.smart.refresh.layout.SmartRefreshLayout
import kotlin.collections.isNullOrEmpty

/**
 * author: allan
 * 适用于放在SmartRefreshLayout里面
 */
abstract class SmartRLBindRcvAdapter<DATA:IViewTypeBean, VH: BindViewHolder<DATA, *>> : BaseAdapter<DATA, VH>(), ILoadMoreAdapter<DATA> {
    private var mRefreshLayout : SmartRefreshLayout? = null

    internal var hasMore = false

    fun setNoMore() {
        hasMore = false
    }

    fun bindRefreshLayout(refreshLayout: SmartRefreshLayout, refreshBlock:(()->Unit)? = null, loadMoreBlock:(()->Unit)? = null) {
        this.mRefreshLayout = refreshLayout

        refreshLayout.setEnableRefresh(refreshBlock != null)
        refreshLayout.setEnableLoadMore(loadMoreBlock != null)

        if (refreshBlock != null) {
            refreshLayout.setOnRefreshListener {
                refreshBlock.invoke()
            }
        }

        if (loadMoreBlock != null) {
            refreshLayout.setOnLoadMoreListener {
                loadMoreBlock.invoke()
            }
        }
    }

    /**
     * 加载更多数据
     */
    override fun appendDatas(appendList: List<DATA>?, hasMore: Boolean) {
        this.hasMore = hasMore

        if (!appendList.isNullOrEmpty()) {
            val realDatas = mutableListOf<DATA>()
            realDatas.addAll(appendList)
            addItems(realDatas)
            if (hasMore) {
                mRefreshLayout?.finishLoadMore()
            } else {
                mRefreshLayout?.finishLoadMoreWithNoMoreData()
            }
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

        initDatasCommon(datas, { a, b->
            createDiffer(a, b)
        }, isTraditionalUpdate) { oldDataSize: Int, newDataSize: Int->
            endInitDatasBlock(oldDataSize, newDataSize)
            mRefreshLayout?.finishRefresh()
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
}