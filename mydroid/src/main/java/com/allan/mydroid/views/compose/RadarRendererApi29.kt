package com.allan.mydroid.views.compose

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/** Android 10 至 12L 以及软件 Canvas 使用三圈错峰扩散波纹。 */
open class RadarRendererApi29(size: Size, color: Color, lineWidth: Float) :
    AbsRadarRenderer(size, color, lineWidth) {
    private val waveStroke = Stroke(width = lineWidth * 1.5f)
    private val waveGlow = Stroke(width = lineWidth * 5f)

    override fun drawScan(scope: DrawScope, phase: Float) {
        repeat(3) { index ->
            val wave = (phase + index / 3f) % 1f
            val fadeIn = if (wave < 0.18f) wave / 0.18f else 1f
            val remaining = 1f - wave
            val opacity = fadeIn * remaining * remaining * 0.42f
            val waveRadius = radius * wave
            scope.drawCircle(color.copy(alpha = opacity * 0.22f), waveRadius, center = origin, style = waveGlow)
            scope.drawCircle(color.copy(alpha = opacity), waveRadius, center = origin, style = waveStroke)
        }
    }
}
