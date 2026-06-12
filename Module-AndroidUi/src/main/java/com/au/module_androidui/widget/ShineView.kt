package com.au.module_androidui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import kotlin.math.abs
import kotlin.math.tan

class ShineView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    View(
        context, attrs, defStyleAttr
    ) {
    private val mPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mPath: Path
    private var mProgress = 0f
    private val mShineWidth = 100
    private var mShineAngle = 30
    private var animator: ValueAnimator? = null

    /** 是否启用闪光动画，默认 true */
    var shineEnabled: Boolean = true
        set(value) {
            field = value
            if (!value) {
                stopAnimation()
            }
        }

    // 新增角度设置方法
    fun setShineAngle(angle: Int) {
        this.mShineAngle = angle
    }

    fun setColor(@ColorInt color:Int) {
        mPaint.setColor(color)
    }

    init {
        mPaint.setColor(Color.WHITE)
        mPaint.style = Paint.Style.FILL
        mPath = Path()
    }

    protected override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!shineEnabled) return
        // 计算斜向矩形路径
        val width: Float = width.toFloat()
        val height: Float = height.toFloat()

        // 修正后的角度计算（移除了冗余的diagonal变量）
        val radians = Math.toRadians(mShineAngle.toDouble())
        val horizontalSlide = (height / tan(radians)).toFloat() // 水平滑动距离
        val verticalSlide = height

        val totalTravel = width + horizontalSlide // 总运动距离
        val offset = mProgress * totalTravel
        val currentPos = offset - horizontalSlide

        mPath.reset()
        mPath.moveTo(currentPos, 0f)
        mPath.lineTo(currentPos + mShineWidth, 0f) // 闪光条宽度
        mPath.lineTo(currentPos + mShineWidth + horizontalSlide, verticalSlide)
        mPath.lineTo(currentPos + horizontalSlide, verticalSlide)
        mPath.close()

        mPaint.setAlpha((255 * (1 - abs(mProgress - 0.5f) * 2)).toInt())
        canvas.drawPath(mPath, mPaint)
    }

    fun startAnimation() {
        if (!shineEnabled) return
        stopAnimation()
        val valueAnimator = ValueAnimator.ofFloat(0f, 1f)
        valueAnimator.setDuration(3000)
        valueAnimator.repeatCount = ValueAnimator.INFINITE
        valueAnimator.addUpdateListener { animation ->
            mProgress = animation.getAnimatedValue() as Float
            invalidate()
        }
        valueAnimator.start()
        animator = valueAnimator
    }

    private fun stopAnimation() {
        animator?.cancel()
        animator = null
        mProgress = 0f
        invalidate()
    }
}
