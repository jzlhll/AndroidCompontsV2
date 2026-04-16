package com.au.module_androidui.widget

import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewTreeObserver
import androidx.constraintlayout.widget.ConstraintLayout
import com.au.module_android.click.onClick
import com.au.module_androidui.databinding.SwitchButtonBinding

/**
 * 黑白块的开关
 * 布局设置的时候，宽度是2x+4 dp。高度是x+4 dp
 */
class SwitchButton @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    ConstraintLayout(
        context, attrs, defStyleAttr
    ) {
    private var mViewBinding = SwitchButtonBinding.inflate(LayoutInflater.from(context), this, true)

    /**
     * 默认就是关闭
     */
    var isClosed = true
        private set

    var isInit = false

    /**
     * 是否阻止
     */
    var abort = false

    private var isPost = false

    /**
     * 点击切换的回调函数
     */
    var valueCallback : ((Boolean)->Unit)? = null

    init {
        mViewBinding.selectBgView.visibility = View.INVISIBLE
        mViewBinding.root.onClick {
            if (!abort) {
                val newIsClosed = !isClosed
                setValue(newIsClosed)
                valueCallback?.invoke(newIsClosed)
            }
        }
        initBeforeFirstDraw()
    }

    fun initValue(close:Boolean) {
        isInit = true
        this.isClosed = close
        updateRootState()
        if (isPost) {
            syncSelectBgPosition()
        }
    }

    fun setValue(close: Boolean) {
        if (!isInit) throw RuntimeException()
        if (isClosed == close) {
            return
        }
        //后续也可能后台改动，进而触发notifyItemChange bindData，则动画
        this.isClosed = close
        updateRootState()
        if (isPost) {
            handleAnimal()
        }
    }

    private fun handleAnimal() {
        val bgAnimator: ObjectAnimator
        val newIsLeftOn = isClosed
        bgAnimator = if (newIsLeftOn) {  //从 右边 -> 左边
            ObjectAnimator.ofFloat(mViewBinding.selectBgView, "translationX", 0f)
        } else { //从 true - false
            ObjectAnimator.ofFloat(
                mViewBinding.selectBgView, "translationX",
                0f, getMoveDistance()
            )
        }
        bgAnimator.duration = 160
        bgAnimator.start()
        updateRootState()
    }

    private fun initBeforeFirstDraw() {
        viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (!viewTreeObserver.isAlive) {
                    return true
                }
                if (mViewBinding.selectBgView.width == 0) {
                    requestLayout()
                    return false
                }
                viewTreeObserver.removeOnPreDrawListener(this)
                syncSelectBgPosition()
                isPost = true
                mViewBinding.selectBgView.visibility = View.VISIBLE
                updateRootState()
                return true
            }
        })
    }

    private fun syncSelectBgPosition() {
        mViewBinding.selectBgView.translationX = if (isClosed) {
            0f
        } else {
            getMoveDistance()
        }
    }

    private fun getMoveDistance(): Float {
        val rootWidth = width - context.resources.getDimension(com.au.module_androidcolor.R.dimen.switch_button_padding) * 2
        val btnWidth = mViewBinding.selectBgView.width
        return rootWidth - btnWidth
    }

    private fun updateRootState() {
        if (!isClosed) {
            mViewBinding.root.setBackgroundResource(com.au.module_androidcolor.R.drawable.switch_btn_opened)
        } else {
            mViewBinding.root.setBackgroundResource(com.au.module_androidcolor.R.drawable.switch_btn_closed)
        }
    }
}