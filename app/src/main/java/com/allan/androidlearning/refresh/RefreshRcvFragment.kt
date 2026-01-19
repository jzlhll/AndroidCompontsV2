package com.allan.androidlearning.refresh

import android.os.Bundle
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.allan.androidlearning.databinding.FragmentRefresh1Binding
import com.allan.androidlearning.databinding.ItemRefresh1Binding
import com.au.module_android.log.logdNoFile
import com.au.module_android.utils.launchOnThread
import com.au.module_android.utils.withMainThread
import com.au.module_androidui.ui.bindings.BindingFragment
import com.au.module_androidui.ui.views.YourToolbarInfo
import com.au.module_nested.recyclerview.DiffCallback
import com.au.module_nested.recyclerview.SmartRLBindRcvAdapter
import com.au.module_nested.recyclerview.viewholder.BindViewHolder
import com.au.module_nested.smartrefresher.setSimpleLoadingFooter
import com.au.module_nested.smartrefresher.setSimpleLoadingHeader
import com.scwang.smart.refresh.footer.ClassicsFooter
import java.util.UUID

/**
 * 常规recyclerView下的，头+脚
 */
class RefreshRcvFragment : BindingFragment<FragmentRefresh1Binding>() {
    private val adapter by lazy { Refresh1Adapter()}

    private var mCount = 0

    override fun onBindingCreated(savedInstanceState: Bundle?) {
        //配置RecyclerView
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@RefreshRcvFragment.adapter
        }

        //配置下拉刷新
        binding.refreshLayout.apply {
            //setRefreshHeader(ClassicsHeader(requireContext()))
            //setRefreshHeader(MaterialHeader(requireContext()))
            setSimpleLoadingHeader()

//            setRefreshFooter(ClassicsFooter(requireContext()))
            setSimpleLoadingFooter()

            //第一个参数是背景色；第二个参数是文字颜色
            setPrimaryColorsId(com.au.module_androidcolor.R.color.windowBackground, com.au.module_androidcolor.R.color.color_text_desc)

            setEnableRefresh(true)//是否启用下拉刷新功能

            setEnableLoadMore(true)//是否启用上拉加载功能
            setEnableAutoLoadMore(true)//是否启用列表惯性滑动到底部时自动加载更多
            setEnableFooterFollowWhenNoMoreData(true) //当没有更多数据的时候，是否一直显示footer

            //下拉刷新监听器
            setOnRefreshListener {
                //模拟网络请求
                loadFirstData()
            }

            setOnLoadMoreListener {
                loadMoreData("load more by refresh")
            }

            autoRefresh()
        }
    }

    private fun loadFirstData() {
        lifecycleScope.launchOnThread {
            Thread.sleep(2000)
            val data = mutableListOf<Bean>()
            val magic = UUID.randomUUID().toString().substring(0, 8)
            for (i in 0 until 30) {
                data.add(Bean("Load-$magic: $i"))
            }
            withMainThread {
                mCount = 0
                adapter.initDatas(data, false)
                binding.refreshLayout.finishRefresh()
            }
        }
    }

    private fun loadMoreData(from:String) {
        logdNoFile { from }
        lifecycleScope.launchOnThread {
            Thread.sleep(300)
            val data = mutableListOf<Bean>()
            val magic = UUID.randomUUID().toString().substring(0, 8)
            val curSize = adapter.itemCount
            for (i in curSize until curSize+30) {
                data.add(Bean("Append-$magic: $i"))
            }
            mCount++
            withMainThread {
                adapter.appendDatas(data)
                if (mCount <= 5) {
                    binding.refreshLayout.finishLoadMore()
                } else {
                    binding.refreshLayout.finishLoadMoreWithNoMoreData()
                }
                //adapter.appendDatas(data, mCount <= 5)
            }
        }
    }

    override fun toolbarInfo(): YourToolbarInfo {
        return YourToolbarInfo.Defaults("Refresh1")
    }
}

class Bean(val str: String)

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