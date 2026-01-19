package com.au.module_nested.smartrefresher

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import com.au.module_android.log.logdNoFile
import com.au.module_nested.R
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.scwang.smart.refresh.layout.api.RefreshHeader
import com.scwang.smart.refresh.layout.api.RefreshLayout
import com.scwang.smart.refresh.layout.constant.RefreshState
import com.scwang.smart.refresh.layout.simple.SimpleComponent

/**
 * 我定制的：
 * 简易的只有一个CircularProgressIndicator的下拉刷新Header
 */
class SimpleLoadingHeader @JvmOverloads constructor(context: Context?, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : AbsSimpleLoading(context, attrs, defStyleAttr), RefreshHeader {
    override fun getLayoutId(): Int {
        return R.layout.srl_simple_loading_header
    }

    override fun getIndicatorId(): Int {
        return R.id.srl_simple_loading_indicator
    }

    override fun initExtraUi(thisView: View) {
        mIndicator.setIndicatorColor(pullColor)
    }

    override fun onMoving(isDragging: Boolean, percent: Float, offset: Int, height: Int, maxDragHeight: Int) {
        super.onMoving(isDragging, percent, offset, height, maxDragHeight)
        if (isDragging) {
            mIndicator.isIndeterminate = false
            mIndicator.progress = (percent * 100).toInt()
        } else {
            mIndicator.isIndeterminate = true
        }
    }

    override fun onStateChanged(refreshLayout: RefreshLayout, oldState: RefreshState, newState: RefreshState) {
        if (enableRandomColor) {
            if (oldState == RefreshState.None) {
                changeIndicatorColor(true)
            }
            if (newState == RefreshState.Refreshing) {
                changeIndicatorColor(false)
            } else {
                changeIndicatorColor(true)
            }
        }
    }

    override fun onFinish(refreshLayout: RefreshLayout, success: Boolean): Int {
        changeIndicatorColor(true)
        return super.onFinish(refreshLayout, success)
    }
}