package com.au.module_androidui.widget

import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.EmptySuper
import com.au.module_android.click.onClick
import com.au.module_android.utils.invisible
import com.au.module_android.utils.visible
import com.au.module_androidui.R
import com.au.module_androidui.databinding.LayoutSwitchButtonsBinding
import kotlin.math.max

/**
 * 自定义SwitchView 全新设计。 滑块。
 * 请注意：width必须设置为wrap_content。代码内部会让2个文字一样宽，与preview不同。请注意。
 */
open class SwitchLayoutButton @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
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

    private var isPost = false

    private val textColor:Int
    private val textSelectColor:Int
    private val textColorDisable:Int
    private val textSelectColorDisable:Int

    private var isDisabled = false

    lateinit var root: ViewGroup
    lateinit var leftTv: TextView
    lateinit var rightTv: TextView
    lateinit var padding : View
    lateinit var selectBgView : View
    lateinit var selectBgViewDisable : View

    @EmptySuper
    open fun initLayoutBinding() {
        LayoutSwitchButtonsBinding.inflate(LayoutInflater.from(context), this, true).let {
            leftTv = it.leftTv
            rightTv = it.rightTv
            root = it.root
            padding = it.padding
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
        leftTv.setPadding(textPaddingHorz, 0, textPaddingHorz, 0)
        rightTv.setPadding(textPaddingHorz, 0, textPaddingHorz, 0)
        leftTv.text = leftStr
        rightTv.text = rightStr

        root.onClick {
            if(isDisabled) return@onClick

            val newIsLeft = !isLeft
            setValue(newIsLeft)
            valueCallback?.invoke(newIsLeft)
        }
        initBeforeFirstDraw(paddingInner)
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
        } else {
            if (isLeft) {
                leftTv.setTextColor(textSelectColor)
                rightTv.setTextColor(textColor)
            } else {
                leftTv.setTextColor(textColor)
                rightTv.setTextColor(textSelectColor)
            }
        }

        if (!isPost) {
            selectBgView.invisible()
            selectBgViewDisable.invisible()
        } else if (isDisabled) {
            selectBgViewDisable.visible()
            selectBgView.invisible()
        } else {
            selectBgView.visible()
            selectBgViewDisable.invisible()
        }
    }

    private fun initBeforeFirstDraw(paddingInner: Int) {
        viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (!viewTreeObserver.isAlive) {
                    return true
                }
                var needRelayout = false

                if (paddingInner == 0) {
                    if (padding.visibility != GONE) {
                        padding.visibility = GONE
                        needRelayout = true
                    }
                } else if (paddingInner > 0) {
                    if (padding.layoutParams.width != paddingInner) {
                        padding.layoutParams = padding.layoutParams.apply {
                            width = paddingInner
                        }
                        needRelayout = true
                    }
                }

                val leftWidth = leftTv.width
                val rightWidth = rightTv.width
                if (leftWidth > 0 && rightWidth > 0) {
                    val targetWidth = max(leftWidth, rightWidth)
                    if (leftTv.layoutParams.width != targetWidth) {
                        leftTv.layoutParams = leftTv.layoutParams.apply {
                            width = targetWidth
                        }
                        needRelayout = true
                    }
                    if (rightTv.layoutParams.width != targetWidth) {
                        rightTv.layoutParams = rightTv.layoutParams.apply {
                            width = targetWidth
                        }
                        needRelayout = true
                    }
                }

                if (needRelayout || selectBgView.width == 0) {
                    requestLayout()
                    return false
                }

                viewTreeObserver.removeOnPreDrawListener(this)
                syncSelectBgPosition()
                isPost = true
                changeTextColor()
                return true
            }
        })
    }

    private fun syncSelectBgPosition() {
        val targetTranslationX = if (isLeft) {
            0f
        } else {
            (selectBgView.width + padding.width).toFloat()
        }
        selectBgView.translationX = targetTranslationX
        selectBgViewDisable.translationX = targetTranslationX
    }

    fun initValue(isLeft:Boolean, disable:Boolean, leftRightStrs:Pair<String, String>? = null) {
        isInit = true
        this.isLeft = isLeft

        isDisabled = disable

        if (leftRightStrs != null) {
            leftTv.text = leftRightStrs.first
            rightTv.text = leftRightStrs.second
        }

        if (isPost) {
            syncSelectBgPosition()
            changeTextColor()
        }
    }

    fun setValue(isLeft: Boolean) {
        if (!isInit) throw RuntimeException()
        if (isDisabled) return
        //后续也可能后台改动，进而触发notifyItemChange bindData，则动画
        this.isLeft = isLeft
        if (isPost) {
            handleAnimal()
            changeTextColor()
        }
    }

    private fun handleAnimal() {
        val bgAnimator: ObjectAnimator
        val newIsLeftOn = isLeft
        bgAnimator = if (newIsLeftOn) {  //从 右边 -> 左边
            ObjectAnimator.ofFloat(selectBgView, "translationX", 0f)
        } else { //从 true - false
            ObjectAnimator.ofFloat(
                selectBgView, "translationX",
                0f, (selectBgView.width + padding.width).toFloat()
            )
        }
        bgAnimator.duration = 160
        bgAnimator.start()
    }
}