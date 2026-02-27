package com.au.module_nested.recyclerview

import android.util.Log
import androidx.annotation.CallSuper
import com.au.module_nested.recyclerview.page.PullRefreshStatus
import com.au.module_nested.recyclerview.viewholder.BindViewHolder
import java.lang.IllegalStateException

/**
 * author: allan
 * Time: 2022/11/22
 * Desc: 基于BaseAdapter：简单的封装，提供diff算法；和一些增删改动的动作；提供基础list。进行二次扩展。
 *
 * 这是一个<多页自动加载框架Adapter，当到了底部会自动加载。也可以当做普通不分页Adapter使用。支持差异更新。
 * 设置 [loadMoreAction] 来支持自动加载下一页loadMore逻辑。
 *
 * 调用[initDatas]来初始化数据和[appendDatas]来追加数据。
 *
 */
abstract class AutoLoadMoreBindRcvAdapter<DATA: IViewTypeBean, VH: BindViewHolder<DATA, *>> :
    BaseAdapter<DATA, VH>(), ILoadMoreAdapter<DATA> {

    internal var hasMore = false

    fun setNoMore() {
        hasMore = false
    }

    /**
     * 如果支持自动触底loadMore下一页，则需要设置这个参数。
     * 它是当onBindViewHolder最后一个数据的时候自动触发。
     * 默认情况return null则不做loadMore。
     */
    var loadMoreAction:(()->Unit)? = null

    //////////////////////////
    ///
    ////////////////////////
    fun supportLoadMore() = loadMoreAction != null

    private fun onLoadMoreInner() {
        status = PullRefreshStatus.LoadingMore
        loadMoreAction?.invoke()
    }

    private var status: PullRefreshStatus = PullRefreshStatus.Normal //默认加载
    fun getCurrentStatus(): PullRefreshStatus = status

    /**
     * 加载更多数据
     */
    override fun appendDatas(appendList: List<DATA>?, hasMore: Boolean) {
        if (!supportLoadMore()) {
            Log.e("allan", "You do not supportLoadMore!")
            throw IllegalStateException("You do not supportLoadMore!")
        }

        status = PullRefreshStatus.Normal
        this.hasMore = hasMore

        if (!appendList.isNullOrEmpty()) {
            val realDatas = mutableListOf<DATA>()
            realDatas.addAll(appendList)
            addItems(realDatas)
        }
    }

    protected open fun endInitDatasBlock(oldDataSize: Int, newDataSize: Int) {
        status = PullRefreshStatus.Normal
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
        }
    }

    @CallSuper
    final override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bindData(datas[position])
        if (supportLoadMore() && hasMore && position == itemCount - 1) {
            onLoadMoreInner()
        }
    }

    /**
     * 当需要进行局部化差异更新的时候，会创建differ。
     */
    protected open fun createDiffer(a:List<DATA>?, b:List<DATA>?): DiffCallback<DATA>? {
        return null
    }

}