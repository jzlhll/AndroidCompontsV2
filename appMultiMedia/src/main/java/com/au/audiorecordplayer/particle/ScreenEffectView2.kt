package com.au.audiorecordplayer.particle

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.hypot

class ScreenEffectView2 @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 配置参数
    private var innerMargin = 48f.dpToPx() // 内部圆角矩形距离边距
    private var cornerRadius = 32f.dpToPx() // 圆角半径
    private var startColor = Color.TRANSPARENT // 渐变起始颜色（内部）
    private var endColor = Color.parseColor("#80ff0000") // 渐变结束颜色（外部边缘）

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var innerRectF = RectF()
    private var outerRectF = RectF()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        // 计算内部圆角矩形区域
        innerRectF.set(
            innerMargin,
            innerMargin,
            w - innerMargin,
            h - innerMargin
        )

        // 外部矩形（全屏）
        outerRectF.set(0f, 0f, w.toFloat(), h.toFloat())

        updateGradientShader()
    }

    private fun updateGradientShader() {
        // 创建从内部矩形边缘到外部边缘的径向渐变
        val centerX = width / 2f
        val centerY = height / 2f

        // 计算渐变半径（从中心到角落的最大距离）
        val maxDistance = hypot(
            (width / 2).toDouble(),
            (height / 2).toDouble()
        ).toFloat()

        // 计算内部矩形的等效半径
        val innerRadius = hypot(
            (width / 2 - innerMargin).toDouble(),
            (height / 2 - innerMargin).toDouble()
        ).toFloat()

        paint.shader = RadialGradient(
            centerX, centerY, maxDistance,
            intArrayOf(startColor, startColor, endColor),
            floatArrayOf(0f, innerRadius / maxDistance, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawSimpleGradient(canvas)
    }

    /**
     * 方法2: 简单的渐变填充（性能更好，但圆角处效果稍差）
     */
    private fun drawSimpleGradient(canvas: Canvas) {
        canvas.drawRect(outerRectF, paint)
    }

    // 公共方法，允许动态更新参数
    fun updateGradient(
        newInnerMargin: Float? = null,
        newCornerRadius: Float? = null,
        newStartColor: Int? = null,
        newEndColor: Int? = null
    ) {
        newInnerMargin?.let {
            innerMargin = it.dpToPx()
            innerRectF.set(
                innerMargin,
                innerMargin,
                width - innerMargin,
                height - innerMargin
            )
        }

        newCornerRadius?.let {
            cornerRadius = it.dpToPx()
        }

        newStartColor?.let {
            startColor = it
            updateGradientShader()
        }

        newEndColor?.let {
            endColor = it
            updateGradientShader()
        }

        invalidate()
    }

    // dp转px的扩展函数
    private fun Float.dpToPx(): Float = this * resources.displayMetrics.density
}