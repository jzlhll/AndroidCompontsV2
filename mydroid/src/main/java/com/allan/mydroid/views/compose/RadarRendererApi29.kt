package com.allan.mydroid.views.compose

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/** Android 10 至 12L 以及软件 Canvas 使用三圈错峰扩散波纹。 */
open class RadarRendererApi29(size: Size, color: Color, lineWidth: Float) :
    AbsRadarRenderer(size, color, lineWidth) {
    private val waveStroke = Stroke(width = lineWidth * 2f)

    override fun drawScan(scope: DrawScope, phase: Float) {
        repeat(3) { index ->
            val wave = (phase + index / 3f) % 1f
            scope.drawCircle(color.copy(alpha = (1f - wave) * 0.8f), radius * wave, style = waveStroke)
        }
    }
}
