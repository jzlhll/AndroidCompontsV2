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
class SimpleLoadingHeader @JvmOverloads constructor(context: Context?, attrs: AttributeSet? = null) : SimpleComponent(context, attrs, 0), RefreshHeader {

    private var mIndicator: CircularProgressIndicator

    private val colors = Colors.loadingColors()

    /**
     * 是否启用随机颜色
     */
    var enableRandomColor: Boolean = true

    /**
     * 下拉时的颜色
     */
    var pullColor : Int = colors[0]

    init {
        inflate(context, R.layout.srl_simple_loading_header, this)
        val thisView: View = this
        mIndicator = thisView.findViewById<CircularProgressIndicator>(R.id.srl_simple_loading_indicator)
        mIndicator.animate().setInterpolator(null)
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
                mIndicator.setIndicatorColor(pullColor)
            }
            if (newState == RefreshState.Refreshing) {
                mIndicator.setIndicatorColor(*colors)
            } else {
                mIndicator.setIndicatorColor(pullColor)
            }
        }
    }
}
