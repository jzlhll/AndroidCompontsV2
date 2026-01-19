package com.au.module_nested.smartrefresher

import com.scwang.smart.refresh.layout.SmartRefreshLayout

fun SmartRefreshLayout.setSimpleLoadingHeader(headerHeight: Float = 42f) : SmartRefreshLayout {
    setRefreshHeader(SimpleLoadingHeader(context))
    setHeaderHeight(headerHeight)
    setHeaderTriggerRate(1.25f)
    return this
}

fun SmartRefreshLayout.setSimpleLoadingFooter() : SmartRefreshLayout {
    setRefreshFooter(SimpleLoadingFooter(context))
    setFooterHeight(32f)
    return this
}
