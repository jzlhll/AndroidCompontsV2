package com.au.audiorecordplayer.particle
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.annotation.RequiresApi
import com.au.module_android.utils.logdNoFile

/**
 * 带圆角矩形和渐变效果的边缘氛围光效
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
open class ScreenEffectView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 配置常量
    companion object {
        // 尺寸配置
        private const val RECT_RATIO_HORZ = 0.85f //减少该数字加大横向区域
        private const val RECT_RATIO_VERT = 0.89f //减少该数字加大竖向区域
        private const val RECT_RATIO_VERT_T = 0.4f //top的占比

        private const val DEFAULT_CORNER_RADIUS = 48.0 // 默认圆角半径, 这里忽略了density，直接自行处理好

        // 动画配置
        private const val ANIMATION_DURATION = 6000L // 动画周期5秒
        protected const val COLOR_CHANGE_SPEED = 1.5f // 颜色变化速度
        private const val MAX_ALPHA = 0.8 //最大透明度

    }

    // 颜色配置 - 调整为更适合深色模式的颜色
    protected val color1 = floatArrayOf(0.2f, 0.2f, 1.0f, 1.0f) // 调整为更亮的蓝色
    protected val color2 = floatArrayOf(0.0f, 0.8f, 1.0f, 1.0f) // 更亮的青色
    protected val color3 = floatArrayOf(0.7f, 0.2f, 1.0f, 1.0f) // 更亮的蓝紫色

    private val shaderSource = """
        uniform float4 color1;   // 边缘颜色1
        uniform float4 color2;   // 边缘颜色2  
        uniform float4 color3;   // 边缘颜色3
        uniform float4 rectProps;    // 圆角矩形参数: x, y, width, height
        uniform float iTime;         // 时间变量，用于动画
        
        // 圆角矩形 SDF 函数
        float roundedBoxSDF(vec2 p, vec2 b, float r) {
            vec2 d = abs(p) - b;
            return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - r;
        }
        
        vec4 mixColors(float time) {
            float factor1 = (sin(time * $COLOR_CHANGE_SPEED) + 1.0) * 0.5;
            float factor2 = (sin(time * $COLOR_CHANGE_SPEED + 2.094) + 1.0) * 0.5; // 2.094 = 2π/3
            float factor3 = (sin(time * $COLOR_CHANGE_SPEED + 4.188) + 1.0) * 0.5; // 4.188 = 4π/3
            
            // 归一化因子，确保颜色强度一致
            float sum = factor1 + factor2 + factor3;
            factor1 /= sum;
            factor2 /= sum; 
            factor3 /= sum;
            
            vec4 mixedColor = color1 * factor1 + color2 * factor2 + color3 * factor3;
            mixedColor.rgb *= 1.2;
            mixedColor.rgb = clamp(mixedColor.rgb, 0.0, 1.0);
            
            return mixedColor;
        }
        
        vec4 main(vec2 fragCoord) {
            // 计算圆角矩形的中心点和半尺寸
            vec2 rectCenter = vec2(rectProps.x + rectProps.z * 0.5, rectProps.y + rectProps.w * 0.5);
            vec2 rectHalfSize = vec2(rectProps.z * 0.5, rectProps.w * 0.5);
            
            // 计算当前像素相对于圆角矩形中心的坐标
            vec2 relativePos = fragCoord - rectCenter;
            
            // 计算到圆角矩形的距离（正数表示在矩形外部，负数表示在内部）
            float distanceToRect = roundedBoxSDF(relativePos, rectHalfSize, $DEFAULT_CORNER_RADIUS);
    
            if (distanceToRect <= 0.0) {
                return vec4(0.0, 0.0, 0.0, 0.0);
            }
            
            // 使用时间动态计算边缘颜色
            vec4 dynamicEdgeColor = mixColors(iTime);
            
            // 使用指数衰减，让近距离alpha快速衰减到0
            float alpha = $MAX_ALPHA * min(distanceToRect / 150.0, 1.0);
            return vec4(dynamicEdgeColor.rgb * alpha, alpha);
        }
    """.trimIndent()

    private val mPaint: Paint = Paint()
    private var valueAnimator: ValueAnimator? = null
    private var shader: RuntimeShader? = null

    // 内圈矩形参数
    protected var rectX = 0f
    protected var rectY = 0f
    protected var rectWidth = 0f
    protected var rectHeight = 0f

    protected var w = 0
    protected var h = 0

    protected var adjustSizeRatio = 1.05f

    // 添加背景绘制支持
    private val backgroundPaint = Paint().apply {
        color = 0x00000000 // 完全透明背景
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        this.w = w
        this.h = h
        // 创建RuntimeShader对象
        shader = RuntimeShader(shaderSource).apply {
            // 设置三种边缘颜色
            setFloatUniform("color1", color1)
            setFloatUniform("color2", color2)
            setFloatUniform("color3", color3)

            // 设置圆角矩形参数
            rectProps(this)

            // 初始时间设为0
            setFloatUniform("iTime", 0f)

            // 应用着色器到Paint
            mPaint.shader = this
        }
        startAnimation()
    }

    private fun rectProps(shade: RuntimeShader?) {
        logdNoFile {"rect props $adjustSizeRatio"}
        rectWidth = w * RECT_RATIO_HORZ / adjustSizeRatio
        rectHeight = h * RECT_RATIO_VERT / adjustSizeRatio
        rectX = (w - rectWidth) * 0.5f
        rectY = (h - rectHeight) * RECT_RATIO_VERT_T

        shade?.setFloatUniform("rectProps",
            rectX,
            rectY,
            rectWidth,
            rectHeight)
    }

    protected open fun onValueAnimatorUpdate(timeValue: Float) {
        // 更新着色器中的时间uniform
        shader?.setFloatUniform("iTime", timeValue)
    }

    private fun startAnimation() {
        // 停止之前的动画
        valueAnimator?.cancel()

        // 创建ValueAnimator，从0到2π循环
        valueAnimator = ValueAnimator.ofFloat(0f, 6.283185f) // 2π ≈ 6.283185
        valueAnimator?.apply {
            duration = ANIMATION_DURATION
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()

            addUpdateListener { animation ->
                // 获取当前动画值（时间）
                val timeValue = animation.animatedValue as Float
                onValueAnimatorUpdate(timeValue)
                // 触发重绘
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 先绘制透明背景，确保没有黑色矩形
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        // 然后绘制边缘光效果
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), mPaint)

    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // 在View从窗口分离时停止动画，防止内存泄漏
        valueAnimator?.cancel()
    }
}