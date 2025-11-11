package com.au.audiorecordplayer.particle

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.RuntimeShader
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.annotation.RequiresApi
import android.os.Build
import kotlin.math.max
import kotlin.math.min

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class WaveParabolaView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        const val MAX_ALPHA = 0.3f
        const val MIN_ALPHA = 0.05f
        const val DEFAULT_ANIMATION_DURATION = 2000L // 2秒

        const val MAX_HEIGHT = 0.035
        const val MIN_HEIGHT = 0.01
    }

    private val agslSource = """
        uniform float waveHeight;
        uniform float2 iResolution;
        uniform float maxAlpha;
        uniform float minAlpha;
        
        half4 main(float2 fragCoord) {
            // 归一化坐标，原点在左下角
            float2 uv = fragCoord / iResolution;
            
            // 翻转Y轴，使原点在左下角
            uv.y = 1.0 - uv.y;
            
            // 根据waveHeight计算抛物线顶点高度
            // waveHeight=0时几乎平（很小的值），waveHeight=1时正常高度
            float currentHeight = $MIN_HEIGHT + waveHeight * ($MAX_HEIGHT - $MIN_HEIGHT);
            
            // 抛物线方程：y = a*(x-0.5)^2 + currentHeight
            // 在x=0和x=1时，y=0
            float a = -4.0 * currentHeight;
            float parabolaY = a * (uv.x - 0.5) * (uv.x - 0.5) + currentHeight;
            
            // 检查当前像素是否在抛物线下方
            if (uv.y <= parabolaY) {
                float relativeHeight = uv.y / parabolaY;
                float alpha = $MIN_ALPHA + ($MAX_ALPHA - $MIN_ALPHA) * (1.0 - relativeHeight);
                // 返回黄色，渐变alpha
                return half4(1.0, 1.0, 0.0, alpha);
            } else {
                // 透明
                return half4(0.0, 0.0, 0.0, 0.0);
            }
        }
    """.trimIndent()

    // 创建RuntimeShader
    private val runtimeShader: RuntimeShader = RuntimeShader(agslSource)
    private val paint = Paint()
    private var waveAnimator: ValueAnimator? = null

    // 内部使用的waveHeight，不暴露给外部
    private var internalWaveHeight: Float = 0f

    // 外部传入的waveRatio加成
    private var waveRatio: Float = 1.0f

    // 动画速度控制
    private var animationDuration: Long = DEFAULT_ANIMATION_DURATION

    init {
        paint.shader = runtimeShader
        setBackgroundColor(0xFF000000.toInt()) // 黑色背景

        // 启动循环动画
        startWaveAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // 更新分辨率uniform
        runtimeShader.setFloatUniform("iResolution", w.toFloat(), h.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val finalWaveHeight = internalWaveHeight * waveRatio
        runtimeShader.setFloatUniform("waveHeight", finalWaveHeight)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    private fun startWaveAnimation() {
        waveAnimator?.cancel()

        waveAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = animationDuration
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE

            addUpdateListener { animation ->
                internalWaveHeight = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    // 外部调用的更新函数
    fun updateWave(newWaveRatio: Float) {
        waveRatio = max(0f, min(2f, newWaveRatio)) // 限制在0-2范围内
        invalidate()
    }

    // 设置动画速度
    fun setAnimationSpeed(durationMs: Long) {
        animationDuration = max(100, durationMs) // 最小100ms
        startWaveAnimation() // 重新启动动画
    }

    // 清理资源
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        waveAnimator?.cancel()
    }
}