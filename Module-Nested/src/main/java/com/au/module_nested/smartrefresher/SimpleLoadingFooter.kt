package com.au.module_nested.smartrefresher

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import com.au.module_android.log.logdNoFile
import com.au.module_nested.R
import com.scwang.smart.refresh.footer.ClassicsFooter.REFRESH_FOOTER_NOTHING
import com.scwang.smart.refresh.layout.api.RefreshFooter
import com.scwang.smart.refresh.layout.api.RefreshLayout
import com.scwang.smart.refresh.layout.constant.RefreshState

class SimpleLoadingFooter @JvmOverloads constructor(context: Context?, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : AbsSimpleLoading(context, attrs, defStyleAttr), RefreshFooter {
    private var mFinishDuration = 500
    private var mNoMoreData = false

    private lateinit var mNoMoreText : TextView

    override fun initExtraUi(thisView: View) {
        mNoMoreText = thisView.findViewById(R.id.srl_simple_loading_nomore_tv)
        mNoMoreText.text = if(REFRESH_FOOTER_NOTHING != null) {
            REFRESH_FOOTER_NOTHING
        } else {
            context?.getString(com.scwang.smart.refresh.footer.classics.R.string.srl_footer_nothing) ?: ""
        }
    }

    override fun getLayoutId(): Int {
        return R.layout.srl_simple_loading_footer
    }

    override fun getIndicatorId(): Int {
        return R.id.srl_simple_loading_indicator
    }

    override fun onFinish(refreshLayout: RefreshLayout, success: Boolean): Int {
        super.onFinish(refreshLayout, success)
        logdNoFile { "onFinish: $success mNoMoreData: $mNoMoreData" }
        if (!mNoMoreData) {
            return mFinishDuration
        }
        return 0
    }

    /**
     * 设置数据全部加载完成，将不能再次触发加载功能
     */
    override fun setNoMoreData(noMoreData: Boolean): Boolean {
        logdNoFile { "setNoMoreData: $noMoreData" }
        if (mNoMoreData != noMoreData) {
            mNoMoreData = noMoreData
            mNoMoreText.visibility = if (noMoreData) VISIBLE else GONE
            mIndicator.visibility = if (noMoreData) GONE else VISIBLE
        }
        return true
    }

    override fun onStateChanged(refreshLayout: RefreshLayout, oldState: RefreshState, newState: RefreshState) {
        logdNoFile { "onStateChanged: $oldState -> $newState mNoMoreData: $mNoMoreData" }
    }
}