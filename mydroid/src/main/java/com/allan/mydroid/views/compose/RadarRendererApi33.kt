package com.allan.mydroid.views.compose

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Android 13 起用硬件 Canvas 绘制旋转扫光和散射粒子，软件 Canvas 回退为波纹。 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class RadarRendererApi33(size: Size, color: Color, lineWidth: Float) :
    RadarRendererApi29(size, color, lineWidth) {
    private val sweep by lazy(LazyThreadSafetyMode.NONE) {
        val shader = RuntimeShader(RadarSweepSource).apply {
            setFloatUniform("origin", origin.x, origin.y)
            setFloatUniform("beamWidth", lineWidth)
            setFloatUniform("radius", radius)
            setColorUniform("tint", color.toArgb())
        }
        ShaderBrush(shader)
    }

    private val particles by lazy(LazyThreadSafetyMode.NONE) {
        val random = Random(33)
        List(192) {
            val emission = random.nextFloat()
            val angle = (emission - random.nextFloat() * 0.06f) * 2.0 * PI
            val radial = Offset(sin(angle).toFloat(), -cos(angle).toFloat())
            val tangent = Offset(-radial.y, radial.x)
            val distance = radius * (0.16f + sqrt(random.nextFloat()) * 0.68f)
            val sizeVariation = random.nextFloat()
            RadarParticle(
                emission = emission,
                lifetime = 0.11f + random.nextFloat() * 0.15f,
                start = origin + radial * distance,
                radialDrift = radial * (radius * (random.nextFloat() * 0.25f - 0.1f)),
                tangentialDrift = tangent * (radius * (random.nextFloat() * 0.36f - 0.28f)),
                dotRadius = lineWidth * (0.55f + sizeVariation * sizeVariation * 1.55f),
                opacity = 0.58f + random.nextFloat() * 0.36f,
            )
        }
    }

    override fun drawScan(scope: DrawScope, phase: Float) {
        if (!scope.drawContext.canvas.nativeCanvas.isHardwareAccelerated) {
            super.drawScan(scope, phase)
            return
        }
        scope.rotate(degrees = 360f * phase, pivot = origin) { drawCircle(sweep, radius, center = origin) }
        scope.drawParticles(phase)
    }

    private fun DrawScope.drawParticles(phase: Float) {
        for (particle in particles) {
            val offset = phase - particle.emission
            // 跨过十二点方向后继续计算在飞粒子的寿命，避免每圈清空拖尾。
            val age = if (offset < 0f) offset + 1f else offset
            if (age >= particle.lifetime) continue

            val progress = age / particle.lifetime
            val fadeIn = if (progress < 0.12f) progress / 0.12f else 1f
            val remaining = 1f - progress
            val opacity = particle.opacity * fadeIn * remaining * remaining
            val head = particle.positionAt(progress)
            val tail = particle.positionAt(max(0f, progress - 0.3f))
            val middle = particle.positionAt(max(0f, progress - 0.12f))
            val dot = particle.dotRadius
            drawLine(color.copy(alpha = opacity * 0.14f), tail, middle, dot * 0.65f, StrokeCap.Round)
            drawLine(color.copy(alpha = opacity * 0.42f), middle, head, dot * 0.95f, StrokeCap.Round)
            drawCircle(color.copy(alpha = opacity * 0.11f), dot * 2.8f, center = head)
            drawCircle(color.copy(alpha = opacity), dot, center = head)
        }
    }

    private data class RadarParticle(
        val emission: Float,
        val lifetime: Float,
        val start: Offset,
        val radialDrift: Offset,
        val tangentialDrift: Offset,
        val dotRadius: Float,
        val opacity: Float,
    ) {
        fun positionAt(progress: Float): Offset =
            start + radialDrift * (progress * (1.4f - 0.4f * progress)) + tangentialDrift * progress
    }
}

// 扫光朝十二点方向，左侧与外缘逐渐淡出，Canvas 负责绕圆心旋转。
private const val RadarSweepSource = """
    uniform float2 origin;
    uniform float beamWidth;
    uniform float radius;
    layout(color) uniform half4 tint;

    half4 main(float2 position) {
        float2 p = position - origin;
        float distanceToBeam = length(float2(p.x, max(p.y, 0.0)));
        float beam = 1.0 - smoothstep(0.0, beamWidth * 3.0, distanceToBeam);
        float angle = atan(-p.x, max(-p.y, 0.001));
        float trail = (p.x < 0.0 && p.y < 0.0) ? 1.0 - smoothstep(0.0, 1.35, angle) : 0.0;
        float edgeFade = 1.0 - smoothstep(radius * 0.60, radius, length(p));
        half alpha = half(max(0.38 * beam, 0.12 * trail) * edgeFade) * tint.a;
        return half4(tint.rgb * alpha, alpha);
    }
"""
