package com.allan.mydroid.views.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/** 绘制共用的雷达底盘、距离环和十字线，扫描效果交给对应版本的实现。 */
abstract class AbsRadarRenderer(size: Size, protected val color: Color, protected val lineWidth: Float) {
    protected val origin = size.center
    protected val radius = size.minDimension / 2f
    private val ringStroke = Stroke(width = lineWidth)
    private val gridColor = color.copy(alpha = 0.24f)
    private val plate = Brush.radialGradient(
        colors = listOf(color.copy(alpha = 0.18f), color.copy(alpha = 0.04f)),
        center = origin,
        radius = radius,
    )

    fun draw(scope: DrawScope, phase: Float?) {
        with(scope) {
            drawCircle(plate, radius)
            for (ring in 1..3) {
                drawCircle(gridColor, (radius - lineWidth / 2f) * ring / 3f, style = ringStroke)
            }
            val horizontal = Offset(radius, 0f)
            val vertical = Offset(0f, radius)
            drawLine(gridColor, origin - horizontal, origin + horizontal, lineWidth)
            drawLine(gridColor, origin - vertical, origin + vertical, lineWidth)
        }
        if (phase != null) drawScan(scope, phase)
    }

    protected abstract fun drawScan(scope: DrawScope, phase: Float)
}
