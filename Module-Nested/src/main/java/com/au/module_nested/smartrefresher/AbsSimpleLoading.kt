package com.au.module_nested.smartrefresher

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.annotation.EmptySuper
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.scwang.smart.refresh.layout.api.RefreshComponent
import com.scwang.smart.refresh.layout.simple.SimpleComponent

abstract class AbsSimpleLoading @JvmOverloads constructor(context: Context?, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
        : SimpleComponent(context, attrs, defStyleAttr), RefreshComponent {

    val mIndicator: CircularProgressIndicator
    private val colors = Colors.loadingColors()
    private var mIsPullColorShown: Boolean = true

    // region 可配置项

    /**
     * 是否启用随机颜色
     */
    var enableRandomColor: Boolean = Colors.sEnableRandomColor

    /**
     * 下拉时的颜色
     */
    var pullColor : Int = Colors.sPullDownColor

    // endregion

    init {
        inflate(context, getLayoutId(), this)
        val thisView: View = this
        mIndicator = thisView.findViewById<CircularProgressIndicator>(getIndicatorId())
        initExtraUi(thisView)
    }

    @EmptySuper
    open fun initExtraUi(thisView: View) {}

    abstract fun getLayoutId(): Int

    abstract fun getIndicatorId(): Int

    fun changeIndicatorColor(toPull: Boolean) {
        if (toPull && !mIsPullColorShown) {
            mIndicator.setIndicatorColor(pullColor)
            mIsPullColorShown = true
        } else if (!toPull && mIsPullColorShown) {
            mIndicator.setIndicatorColor(*colors)
            mIsPullColorShown = false
        }
    }
}