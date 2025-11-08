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

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
open class ScreenEffectView2 @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 配置常量
    companion object {
        // 尺寸配置
        private const val RECT_SIZE_RATIO_H = 0.82f
        private const val RECT_SIZE_RATIO_V = 0.9f
        private const val RECT_SIZE_RATIO_V_TOP = 0.45f

        private const val DEFAULT_CORNER_RADIUS = 16f        // 默认圆角半径
        private const val CORNER_RADIUS_FIX_RATIO = 0.4f

        private const val MAX_GRADIENT_DISTANCE_FACTOR = 0.08f

        // 动画配置
        private const val ANIMATION_DURATION = 6000L // 动画周期5秒
        protected const val COLOR_CHANGE_SPEED = 1.5f // 颜色变化速度
    }

    // 颜色配置 - 调整为更适合深色模式的颜色
    protected val color1 = floatArrayOf(0.2f, 0.2f, 1.0f, 1.0f) // 调整为更亮的蓝色
    protected val color2 = floatArrayOf(0.0f, 0.8f, 1.0f, 1.0f) // 更亮的青色
    protected val color3 = floatArrayOf(0.7f, 0.2f, 1.0f, 1.0f) // 更亮的蓝紫色

    // 修正后的AGSL着色器代码 - 针对深色模式优化
    private val shaderSource = """
        uniform float2 resolution;   // 屏幕分辨率
        uniform float4 color1;   // 边缘颜色1
        uniform float4 color2;   // 边缘颜色2  
        uniform float4 color3;   // 边缘颜色3
        uniform float4 rectProps;    // 圆角矩形参数: x, y, width, height
        uniform float radius;        // 圆角半径
        uniform float iTime;         // 时间变量，用于动画
        
        // 圆角矩形 SDF 函数
        float roundedBoxSDF(vec2 p, vec2 b, float r) {
            vec2 d = abs(p) - b;
            return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - r;
        }
        
        // 修正的颜色混合函数 - 针对深色模式优化
        vec4 mixColors(float time) {
            // 使用三角函数创建循环的颜色过渡
            float factor1 = (sin(time * $COLOR_CHANGE_SPEED) + 1.0) * 0.5;
            float factor2 = (sin(time * $COLOR_CHANGE_SPEED + 2.094) + 1.0) * 0.5; // 2.094 = 2π/3
            float factor3 = (sin(time * $COLOR_CHANGE_SPEED + 4.188) + 1.0) * 0.5; // 4.188 = 4π/3
            
            // 归一化因子，确保颜色强度一致
            float sum = factor1 + factor2 + factor3;
            factor1 /= sum;
            factor2 /= sum; 
            factor3 /= sum;
            
            // 混合三种颜色
            vec4 mixedColor = color1 * factor1 + color2 * factor2 + color3 * factor3;
            
            // 针对深色模式调整亮度和饱和度
            // 增加亮度，使颜色在深色背景下更显眼
            mixedColor.rgb *= 1.2;
            
            // 确保颜色值在有效范围内
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
            float distanceToRect = roundedBoxSDF(relativePos, rectHalfSize, radius);
            
            // 关键修正：确保圆角矩形内部完全透明
            if (distanceToRect <= 0.0) {
                // 在深色模式下，确保完全透明，不显示任何颜色
                return vec4(0.0, 0.0, 0.0, 0.0);
            }
            
            // 修正：使用常量控制渐变距离
            float maxGradientDistance = length(resolution) * $MAX_GRADIENT_DISTANCE_FACTOR;
            
            // 使用更平滑的渐变函数
            float gradient = smoothstep(0.0, maxGradientDistance, distanceToRect);
            
            // 针对深色模式调整渐变强度
            gradient = pow(gradient, 0.7); // 使用幂函数使渐变更柔和
            
            // 使用时间动态计算边缘颜色
            vec4 dynamicEdgeColor = mixColors(iTime);
            
            // 返回颜色，alpha通道根据渐变变化，RGB使用动态颜色
            // 在深色模式下，确保透明度正确应用
            return vec4(dynamicEdgeColor.rgb, dynamicEdgeColor.a * gradient);
        }
    """.trimIndent()

    private val mPaint: Paint = Paint()
    private var valueAnimator: ValueAnimator? = null
    private var shader: RuntimeShader? = null
    private var cornerRadius = DEFAULT_CORNER_RADIUS // 动态圆角半径

    // 内圈矩形参数
    protected var rectX = 0f
    protected var rectY = 0f
    protected var rectWidth = 0f
    protected var rectHeight = 0f

    // 添加背景绘制支持
    private val backgroundPaint = Paint().apply {
        color = 0x00000000 // 完全透明背景
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        // 创建RuntimeShader对象
        shader = RuntimeShader(shaderSource)
        setupShader(w, h)
        startAnimation()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        cornerRadius = calRoundedCornerPadding(DEFAULT_CORNER_RADIUS, CORNER_RADIUS_FIX_RATIO)
        super.onLayout(changed, left, top, right, bottom)
    }

    private fun setupShader(w: Int, h: Int) {
        shader?.let { shader ->
            // 配置着色器参数
            shader.setFloatUniform("resolution", w.toFloat(), h.toFloat())

            // 设置三种边缘颜色
            shader.setFloatUniform("color1", color1)
            shader.setFloatUniform("color2", color2)
            shader.setFloatUniform("color3", color3)

            // 设置圆角矩形参数
            rectWidth = w * RECT_SIZE_RATIO_H
            rectHeight = h * RECT_SIZE_RATIO_V
            rectX = (w - rectWidth) * 0.5f
            rectY = (h - rectHeight) * RECT_SIZE_RATIO_V_TOP
            shader.setFloatUniform("rectProps", rectX, rectY, rectWidth, rectHeight)

            // 设置圆角半径
            shader.setFloatUniform("radius", cornerRadius)

            // 初始时间设为0
            shader.setFloatUniform("iTime", 0f)

            // 应用着色器到Paint
            mPaint.shader = shader
        }
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