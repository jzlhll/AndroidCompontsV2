package com.au.audiorecordplayer.particle

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RuntimeShader
import android.os.Build
import android.util.AttributeSet
import android.view.View
import androidx.annotation.RequiresApi

/**
 * 固定一个颜色，固定区域，不动的效果很不错的全屏边缘特效
 */
class ScreenEffectView1 @JvmOverloads constructor(
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
        private const val RECT_SIZE_RATIO_VERTICAL = 0.88f
        private const val RECT_SIZE_RATIO_HORIZONTAL = 0.8f
        private const val CORNER_RADIUS = 16f

        // 着色器配置
        private const val MAX_GRADIENT_DISTANCE_FACTOR = 0.09f

        // 修正的 AGSL 着色器代码
        // 修改后的着色器代码 - 更精确地控制混合
        private val shaderSource = """
            uniform float2 resolution;
            uniform float4 edgeColor;
            uniform float4 rectProps;
            uniform float radius;
            
            float roundedBoxSDF(vec2 p, vec2 b, float r) {
                vec2 d = abs(p) - b;
                return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - r;
            }
            
            vec4 main(vec2 fragCoord) {
                vec2 rectCenter = vec2(rectProps.x + rectProps.z * 0.5, rectProps.y + rectProps.w * 0.5);
                vec2 rectHalfSize = vec2(rectProps.z * 0.5, rectProps.w * 0.5);
                
                vec2 relativePos = fragCoord - rectCenter;
                float distanceToRect = roundedBoxSDF(relativePos, rectHalfSize, radius);
                
                // 完全透明的内部区域
                if (distanceToRect <= 0.0) {
                    return vec4(0.0, 0.0, 0.0, 0.0);
                }
                
                // 边缘渐变区域
                float maxGradientDistance = length(resolution) * $MAX_GRADIENT_DISTANCE_FACTOR;
                float gradient = smoothstep(0.0, maxGradientDistance, distanceToRect);
                
                // 只在不完全透明的地方应用颜色
                if (gradient > 0.01) {
                    return vec4(edgeColor.rgb, edgeColor.a * gradient);
                } else {
                    return vec4(0.0, 0.0, 0.0, 0.0);
                }
            }
        """.trimIndent()
    }

    private val mPaint: Paint = Paint().apply {
        isAntiAlias = true
    }

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
        val rectWidth = w * RECT_SIZE_RATIO_HORIZONTAL
        val rectHeight = h * RECT_SIZE_RATIO_VERTICAL
        val rectX = (w - rectWidth) / 2f
        val rectY = (h - rectHeight) / 2f
        shader.setFloatUniform("rectProps", rectX, rectY, rectWidth, rectHeight)

        // 设置圆角半径
        shader.setFloatUniform("radius", CORNER_RADIUS.dpToPx())

        // 应用着色器到 Paint
        mPaint.shader = shader
    }

    private val MODE_DEBUG = false

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (MODE_DEBUG) {
            // 方法2：使用离屏缓冲和正确的混合模式
            val saved = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
            // 先绘制下层内容（这里实际上不会绘制，只是保留）
            // 然后使用 DST_OVER 模式绘制我们的效果
            mPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), mPaint)
            mPaint.xfermode = null
            canvas.restoreToCount(saved)
        } else {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), mPaint)
        }
    }

    private fun Float.dpToPx(): Float = this * resources.displayMetrics.density
}