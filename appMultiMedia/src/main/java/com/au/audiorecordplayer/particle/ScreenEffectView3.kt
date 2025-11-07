package com.au.audiorecordplayer.particle

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import android.util.AttributeSet
import android.view.View
import androidx.annotation.RequiresApi

class ScreenEffectView3 @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 配置常量
    companion object {
        // 颜色配置
        private const val EDGE_COLOR_R = 0.0f
        private const val EDGE_COLOR_G = 0.0f
        private const val EDGE_COLOR_B = 1.0f
        private const val EDGE_COLOR_A = 1.0f

        // 尺寸配置
        private const val RECT_SIZE_RATIO = 0.72f  // 矩形相对于屏幕的大小比例，
        private const val CORNER_RADIUS = 6f     // 圆角半径

        // 着色器配置
        private const val MAX_GRADIENT_DISTANCE_FACTOR = 0.3f  // 最大渐变距离因子

        // 修正的 AGSL 着色器代码 - 从屏幕边缘到圆角矩形的渐变
        private val shaderSource = """
        uniform float2 resolution;   // 屏幕分辨率
        uniform float4 edgeColor;    // 边缘颜色
        uniform float4 rectProps;    // 圆角矩形参数: x, y, width, height
        uniform float radius;        // 圆角半径
        
        // 圆角矩形 SDF 函数
        float roundedBoxSDF(vec2 p, vec2 b, float r) {
            vec2 d = abs(p) - b;
            return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - r;
        }
        
        vec4 main(vec2 fragCoord) {
            // 计算圆角矩形的中心点和半尺寸
            vec2 rectCenter = vec2(rectProps.x + rectProps.z * 0.5, rectProps.y + rectProps.w * 0.5);
            vec2 rectHalfSize = vec2(rectProps.z * 0.5, rectProps.w * 0.5);
            
            // 计算当前像素相对于圆角矩形中心的坐标
            vec2 relativePos = fragCoord - rectCenter;
            
            // 计算到圆角矩形的距离（正数表示在矩形外部，负数表示在内部）
            float distanceToRect = roundedBoxSDF(relativePos, rectHalfSize, radius);
            
            // 关键修改：从屏幕边缘到圆角矩形边缘的渐变
            // 当 distanceToRect > 0 时，我们在圆角矩形外部
            // 我们想要的效果：在圆角矩形外部有颜色，渐变到圆角矩形边缘时变为透明
            
            // 计算渐变因子：0在圆角矩形边缘，1在屏幕最远处
            float gradient = 0.0;
            
            if (distanceToRect > 0.0) {
                // 在圆角矩形外部，计算渐变
                // 使用平滑的渐变，基于到圆角矩形的距离
                float maxGradientDistance = length(resolution) * $MAX_GRADIENT_DISTANCE_FACTOR; // 最大渐变距离
                gradient = smoothstep(0.0, maxGradientDistance, distanceToRect);
            }
            
            // 应用渐变：在圆角矩形内部完全透明，外部根据距离渐变
            float alpha = gradient;
            
            // 返回颜色，alpha 通道根据渐变变化
            return vec4(edgeColor.rgb, edgeColor.a * alpha);
        }
    """.trimIndent()
    }

    private val mPaint: Paint = Paint()

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        // 创建 RuntimeShader 对象
        val shader = RuntimeShader(shaderSource)

        // 配置着色器参数
        shader.setFloatUniform("resolution", w.toFloat(), h.toFloat())

        // 设置边缘颜色（使用常量）
        val edgeColorArray = floatArrayOf(EDGE_COLOR_R, EDGE_COLOR_G, EDGE_COLOR_B, EDGE_COLOR_A)
        shader.setFloatUniform("edgeColor", edgeColorArray)

        // 设置圆角矩形参数 (x, y, width, height)
        // 使用常量配置矩形大小
        val rectWidth = w * RECT_SIZE_RATIO
        val rectHeight = h * RECT_SIZE_RATIO
        val rectX = (w - rectWidth) * 0.5f  // 居中计算
        val rectY = (h - rectHeight) * 0.5f // 居中计算
        shader.setFloatUniform("rectProps", rectX, rectY, rectWidth, rectHeight)

        // 设置圆角半径（使用常量）
        shader.setFloatUniform("radius", CORNER_RADIUS.dpToPx())

        // 应用着色器到 Paint
        mPaint.shader = shader
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), mPaint)
    }

    private fun Float.dpToPx(): Float = this * resources.displayMetrics.density
}