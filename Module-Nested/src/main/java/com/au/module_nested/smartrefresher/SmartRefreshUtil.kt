package com.au.module_nested.smartrefresher

import androidx.core.widget.NestedScrollView
import com.scwang.smart.refresh.layout.SmartRefreshLayout

/**
 * 设置简单的我的简易效果，加载头，默认高度为42dp，触发率为1.25倍。
 */
fun SmartRefreshLayout.setSimpleLoadingHeader(headerHeight: Float = 42f) : SmartRefreshLayout {
    setRefreshHeader(SimpleLoadingHeader(context))
    setHeaderHeight(headerHeight)
    setHeaderTriggerRate(1.25f)
    return this
}

/**
 * 禁用加载更多和下拉刷新功能，同时开启越界拖动以实现视觉上的假下拉效果。
 */
fun SmartRefreshLayout.setNoLoadMoreAndFakePull() : SmartRefreshLayout {
    setEnableLoadMore(false)
    setEnableAutoLoadMore(false)
    // 1. 核心：禁用刷新功能（彻底关闭刷新触发逻辑，不会执行onRefresh回调）
    setEnableRefresh(false)
    // 2. 保留下拉弹性效果（开启越界拖动，实现下拉回弹的视觉假下拉体验）
    setEnableOverScrollDrag(true) //允许你‌拉得更远‌（越界拖动）。
    setEnableOverScrollBounce(true) //让你‌松手后自动弹回‌（越界回弹）。
    return this
}

/**
 * 与SmartRefreshLayout兼容，禁用嵌套滚动，设置OVER_SCROLL_NEVER(可以改善难看的回弹)，填充视口。
 */
fun NestedScrollView.setCompatWithSmartRefreshLayout() : NestedScrollView {
    isNestedScrollingEnabled = true
    overScrollMode = NestedScrollView.OVER_SCROLL_NEVER
    isFillViewport = true
    return this
}

/**
 * 设置简单的我的简易效果，加载脚。
 */
fun SmartRefreshLayout.setSimpleLoadingFooter() : SmartRefreshLayout {
    setRefreshFooter(SimpleLoadingFooter(context))
    setFooterHeight(32f)
    return this
}
