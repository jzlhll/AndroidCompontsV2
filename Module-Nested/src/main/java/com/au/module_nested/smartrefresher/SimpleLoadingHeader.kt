package com.au.module_nested.smartrefresher

import android.content.Context
import android.util.AttributeSet
import android.view.View
import com.au.module_android.utils.visible
import com.au.module_nested.R
import com.scwang.smart.refresh.layout.api.RefreshHeader
import com.scwang.smart.refresh.layout.api.RefreshLayout
import com.scwang.smart.refresh.layout.constant.RefreshState
import kotlin.math.max
import kotlin.math.min

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
        hideIndicator()
    }

    override fun onMoving(isDragging: Boolean, percent: Float, offset: Int, height: Int, maxDragHeight: Int) {
        super.onMoving(isDragging, percent, offset, height, maxDragHeight)
        if (!isDragging) {
            return
        }
        mIndicator.visible()
        mIndicator.isIndeterminate = false
        mIndicator.progress = min(100, max(0, (percent * 100).toInt()))
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
        when (newState) {
            RefreshState.None -> hideIndicator()
            else -> {
                when {
                    newState.isOpening -> {
                        mIndicator.visibility = View.VISIBLE
                        mIndicator.isIndeterminate = true
                    }
                    newState.isDragging || newState.isReleaseToOpening -> {
                        mIndicator.visibility = View.VISIBLE
                        mIndicator.isIndeterminate = false
                    }
                    newState == RefreshState.RefreshReleased -> {
                        mIndicator.visibility = View.VISIBLE
                        mIndicator.isIndeterminate = true
                    }
                    newState.isFinishing -> hideIndicator()
                    else -> hideIndicator()
                }
            }
        }
    }

    override fun onFinish(refreshLayout: RefreshLayout, success: Boolean): Int {
        changeIndicatorColor(true)
        hideIndicator()
        return super.onFinish(refreshLayout, success)
    }

    private fun hideIndicator() {
        mIndicator.isIndeterminate = false
        mIndicator.progress = 0
        mIndicator.visibility = View.GONE
    }
}