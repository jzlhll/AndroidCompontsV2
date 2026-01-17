package com.allan.androidlearning.refresh

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.allan.androidlearning.databinding.FragmentRefresh1Binding
import com.au.module_android.log.logdNoFile
import com.au.module_android.utils.launchOnThread
import com.au.module_android.utils.withMainThread
import com.au.module_androidui.ui.bindings.BindingFragment
import com.au.module_androidui.ui.views.YourToolbarInfo
import com.scwang.smart.refresh.footer.ClassicsFooter
import com.scwang.smart.refresh.header.ClassicsHeader
import com.scwang.smart.refresh.header.MaterialHeader
import com.scwang.smart.refresh.header.TwoLevelHeader
import java.util.UUID

class Refresh1Fragment : BindingFragment<FragmentRefresh1Binding>() {
    private val adapter by lazy { Refresh1Adapter()}

    private var mCount = 0

    override fun onBindingCreated(savedInstanceState: Bundle?) {
        //配置RecyclerView
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@Refresh1Fragment.adapter
        }

        //配置下拉刷新
        binding.refreshLayout.apply {
            //setRefreshHeader(ClassicsHeader(requireContext()))
            //setRefreshHeader(TwoLevelHeader(requireContext()))
            setRefreshHeader(MaterialHeader(requireContext()))

            setRefreshFooter(ClassicsFooter(requireContext()))

            //第一个参数是背景色；第二个参数是文字颜色
            setPrimaryColorsId(com.au.module_androidcolor.R.color.windowBackground, com.au.module_androidcolor.R.color.color_text_desc)
            //setHeaderHeight(50f) //Header标准高度（显示下拉高度>=标准高度 触发刷新）

            setEnableRefresh(true)//是否启用下拉刷新功能
            setEnableLoadMore(true)//是否启用上拉加载功能

            setEnableAutoLoadMore(true)//是否启用列表惯性滑动到底部时自动加载更多

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

     //   loadFirstData()
    }

    private fun loadFirstData() {
        lifecycleScope.launchOnThread {
            Thread.sleep(5000)
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
            Thread.sleep(500)
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