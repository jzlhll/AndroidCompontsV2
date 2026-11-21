package com.allan.mydroid.views.compose

import android.graphics.Paint
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** 绘制共用的柔光底盘，扫描效果由各版本的渲染器处理。 */
abstract class AbsRadarRenderer(size: Size, protected val color: Color, protected val lineWidth: Float) {
    protected val origin = size.center
    protected val radius = size.minDimension / 2f
    private val plate = Brush.radialGradient(
        0f to color.copy(alpha = 0.16f),
        0.52f to color.copy(alpha = 0.09f),
        0.82f to color.copy(alpha = 0.035f),
        1f to color.copy(alpha = 0f),
        center = origin,
        radius = radius,
    )
    private val rimStroke = Stroke(width = min(lineWidth, radius))
    private val rimColor = color.copy(alpha = 0.28f)
    private val smallDotPaint = particlePaint(lineWidth * 1.05f, 0.16f)
    private val largeDotPaint = particlePaint(lineWidth * 1.75f, 0.23f)
    private val dots = run {
        val random = Random(29)
        val small = ArrayList<Float>()
        val large = ArrayList<Float>()
        val count = 104
        val goldenAngle = PI * (3.0 - sqrt(5.0))
        // 按圆盘面积铺点，轻微扰动角度，避免背景粒子排成网格。
        repeat(count) { index ->
            val angle = index * goldenAngle + (random.nextDouble() - 0.5) * 0.85
            val distance = radius * 0.95 * sqrt((index + random.nextDouble()) / count)
            val points = if (random.nextFloat() < 0.24f) large else small
            points.add(origin.x + (cos(angle) * distance).toFloat())
            points.add(origin.y + (sin(angle) * distance).toFloat())
        }
        small.toFloatArray() to large.toFloatArray()
    }

    fun draw(scope: DrawScope, phase: Float?) {
        scope.drawCircle(plate, radius, center = origin)
        scope.drawCircle(rimColor, radius - rimStroke.width / 2f, center = origin, style = rimStroke)
        val canvas = scope.drawContext.canvas.nativeCanvas
        canvas.drawPoints(dots.first, smallDotPaint)
        canvas.drawPoints(dots.second, largeDotPaint)
        if (phase != null) drawScan(scope, phase)
    }

    protected abstract fun drawScan(scope: DrawScope, phase: Float)

    private fun particlePaint(width: Float, opacity: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = this@AbsRadarRenderer.color.copy(alpha = opacity).toArgb()
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
    }
}
