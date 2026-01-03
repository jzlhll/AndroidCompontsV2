package com.au.module_androidui.widget

import com.au.module_android.click.onClick
import com.au.module_android.utils.invisible
import com.au.module_android.utils.visible
import com.au.module_androidui.R
import com.au.module_androidui.databinding.LayoutSwitchButtons2Binding
import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.EmptySuper

/**
 * 自定义SwitchView 简化版本。支持外部约束宽度和高度，内容左右分半显示。
 */
open class SwitchLayoutButton2 @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    FrameLayout(
        context, attrs, defStyleAttr
    ) {
    var isLeft = true
        private set

    private var isInit = false
    fun isInited() = isInit

    /**
     * 点击切换的回调函数
     */
    var valueCallback : ((isLeft:Boolean)->Unit)? = null

    private val textColor:Int
    private val textSelectColor:Int
    private val textColorDisable:Int
    private val textSelectColorDisable:Int

    private var isDisabled = false

    protected lateinit var root: ViewGroup
    protected lateinit var leftTv: TextView
    protected lateinit var rightTv: TextView
    protected lateinit var selectBgView : View
    protected lateinit var selectBgViewDisable : View

    @EmptySuper
    protected open fun initLayoutBinding() {
        LayoutSwitchButtons2Binding.inflate(LayoutInflater.from(context), this, true).let {
            leftTv = it.leftTv
            rightTv = it.rightTv
            root = it.root
            selectBgView = it.selectBgView
            selectBgViewDisable = it.selectBgViewDisable
        }
    }

    init {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.SwitchLayoutButton)

        textColor = typedArray.getColor(R.styleable.SwitchLayoutButton_text_color,
            context.getColor(com.au.module_androidcolor.R.color.color_text_normal))
        textSelectColor = typedArray.getColor(R.styleable.SwitchLayoutButton_select_text_color,
            context.getColor(com.au.module_androidcolor.R.color.color_text_normal))

        textColorDisable = typedArray.getColor(R.styleable.SwitchLayoutButton_text_color_disable,
            context.getColor(com.au.module_androidcolor.R.color.color_switch_block_text_dis))
        textSelectColorDisable = typedArray.getColor(R.styleable.SwitchLayoutButton_select_text_color_disable,
            context.getColor(com.au.module_androidcolor.R.color.color_switch_block_text_sel_dis))

        val textPaddingHorz = typedArray.getDimension(R.styleable.SwitchLayoutButton_text_padding_horz, -1f).toInt()

        val leftStr = typedArray.getString(R.styleable.SwitchLayoutButton_first_str)
        val rightStr = typedArray.getString(R.styleable.SwitchLayoutButton_second_str)

        val paddingInner = typedArray.getDimension(R.styleable.SwitchLayoutButton_padding_inner, -1f).toInt()

        typedArray.recycle()

        initLayoutBinding()
        
        // 设置内边距
        if (textPaddingHorz > 0) {
            leftTv.setPadding(textPaddingHorz, 0, textPaddingHorz, 0)
            rightTv.setPadding(textPaddingHorz, 0, textPaddingHorz, 0)
        }
        
        // 设置文本
        leftTv.text = leftStr
        rightTv.text = rightStr
        
        // 点击事件
        root.onClick {
            if(isDisabled) return@onClick
            switchIt()
        }
    }

    private fun updateSelectionState() {
        // 更新背景位置
        if (!isLeft) {
            val halfWidth = selectBgView.width
            selectBgView.translationX = halfWidth.toFloat()
            selectBgViewDisable.translationX = halfWidth.toFloat()
        } else {
            selectBgView.translationX = 0f
            selectBgViewDisable.translationX = 0f
        }
        
        // 更新文本颜色
        changeTextColor()
    }

    private fun changeTextColor() {
        if (isDisabled) {
            if(isLeft) {
                leftTv.setTextColor(textSelectColorDisable)
                rightTv.setTextColor(textColorDisable)
            } else {
                rightTv.setTextColor(textSelectColorDisable)
                leftTv.setTextColor(textColorDisable)
            }
            selectBgViewDisable.visible()
            selectBgView.invisible()
        } else {
            if (isLeft) {
                leftTv.setTextColor(textSelectColor)
                rightTv.setTextColor(textColor)
            } else {
                leftTv.setTextColor(textColor)
                rightTv.setTextColor(textSelectColor)
            }
            selectBgViewDisable.invisible()
            selectBgView.visible()
        }
    }

    fun initValue(isLeft:Boolean, disable:Boolean, leftRightStrs:Pair<String, String>? = null) {
        this.isLeft = isLeft
        isDisabled = disable
        isInit = true

        if (leftRightStrs != null) {
            leftTv.text = leftRightStrs.first
            rightTv.text = leftRightStrs.second
        }

        // 更新状态
        updateSelectionState()
    }

    fun setValue(isLeft: Boolean) {
        if (!isInit) throw RuntimeException("SwitchLayoutButton2 not initialized. Call initValue() first.")
        if (isDisabled) return
        if (this.isLeft == isLeft) return
        
        this.isLeft = isLeft
        
        // 执行动画
        val halfWidth = selectBgView.width
        val targetX = if (isLeft) 0f else halfWidth.toFloat()
        
        val bgAnimator = ObjectAnimator.ofFloat(selectBgView, "translationX", targetX)
        bgAnimator.duration = 160
        bgAnimator.start()
        
        // 同步更新禁用状态下的背景位置
        selectBgViewDisable.translationX = targetX
        
        // 更新文本颜色
        changeTextColor()
    }

    /*
     * 点击
     */
    fun switchIt() {
        val newIsLeft = !isLeft
        setValue(newIsLeft)
        valueCallback?.invoke(newIsLeft)
    }
}