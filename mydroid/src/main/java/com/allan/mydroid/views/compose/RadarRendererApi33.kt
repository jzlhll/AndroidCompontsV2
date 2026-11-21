package com.allan.mydroid.views.compose

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb

/** Android 13 起在硬件 Canvas 上绘制带拖尾的 AGSL 扫线。 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class RadarRendererApi33(size: Size, color: Color, lineWidth: Float) :
    RadarRendererApi29(size, color, lineWidth) {
    private val sweep by lazy(LazyThreadSafetyMode.NONE) {
        val shader = RuntimeShader(RadarSweepSource).apply {
            setFloatUniform("origin", origin.x, origin.y)
            setFloatUniform("beamWidth", lineWidth)
            setColorUniform("tint", color.toArgb())
        }
        ShaderBrush(shader)
    }

    override fun drawScan(scope: DrawScope, phase: Float) {
        if (scope.drawContext.canvas.nativeCanvas.isHardwareAccelerated) {
            scope.rotate(degrees = 360f * phase) { drawCircle(sweep, radius) }
        } else {
            super.drawScan(scope, phase)
        }
    }
}

// 扫线朝十二点方向，左侧扇区逐渐淡出。整束扫线由 Canvas 绕圆心旋转。
private const val RadarSweepSource = """
    uniform float2 origin;
    uniform float beamWidth;
    layout(color) uniform half4 tint;

    half4 main(float2 position) {
        float2 p = position - origin;
        float distanceToBeam = length(float2(p.x, max(p.y, 0.0)));
        float beam = 1.0 - smoothstep(0.0, beamWidth * 1.5, distanceToBeam);
        float angle = atan(-p.x, max(-p.y, 0.001));
        float trail = (p.x < 0.0 && p.y < 0.0) ? 1.0 - smoothstep(0.0, 1.5, angle) : 0.0;
        half alpha = half(max(0.85 * beam, 0.48 * trail)) * tint.a;
        return half4(tint.rgb * alpha, alpha);
    }
"""
