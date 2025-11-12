package com.au.audiorecordplayer.particle
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.util.AttributeSet
import androidx.annotation.RequiresApi
import kotlin.math.min
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 粒子特效，继承自氛围特效。叠加效果
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class ScreenEffectParticleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : CombinedScreenEffectView(context, attrs, defStyleAttr) {

    // 配置常量
    companion object {
        // 粒子系统配置
        private const val PARTICLE_COUNT = 80
        private const val PARTICLE_SIZE_MIN = 1.5f
        private const val PARTICLE_SIZE_MAX = 4.0f
        private const val PARTICLE_SPEED_MIN = 0.8f
        private const val PARTICLE_SPEED_MAX = 2.5f
        private const val PARTICLE_EMISSION_RATE = 8 // 每帧发射粒子数
        private const val PARTICLE_LIFETIME_MIN = 1.5f
        private const val PARTICLE_LIFETIME_MAX = 3.0f
        private const val PARTICLE_SPREAD_ANGLE = 35 // 发射角度范围（度）
        private const val VERTICAL_SPEED_RATIO = 0.45f // 调整这个值来控制向上距离
        private const val MIX_COLOR = 0.25f //粒子混合白色比例
        private const val MIX_COLOR_R = 1 - MIX_COLOR //1 - 粒子混合白色比例
    }

    // 粒子类
    private class Particle(
        var x: Float,
        var y: Float,
        var velocityX: Float,
        var velocityY: Float,
        var size: Float,
        var lifetime: Float,
        var maxLifetime: Float,
        var currentColor: FloatArray // 当前颜色（混合白色后）
    )

    // 粒子系统相关
    private val particles = mutableListOf<Particle>()
    private val particlePaint = Paint().apply {
        isAntiAlias = true
    }
    private val currentMixColor: FloatArray = floatArrayOf(0f, 0f, 0f, 1f)
    private var lastEmissionTime = 0L
    private val random = Random(System.currentTimeMillis())

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // 初始化一些粒子
        initializeParticles(w, h)
    }

     override fun onValueAnimatorUpdate(timeValue: Float) {
     	super.onValueAnimatorUpdate(timeValue)
        // 更新当前mixColor用于粒子
        updateCurrentMixColor(timeValue)
        // 发射新粒子
        emitParticles()
        // 更新所有粒子
        updateParticles()
     }

    // 初始化粒子
    private fun initializeParticles(width: Int, height: Int) {
        particles.clear()
        for (i in 0 until PARTICLE_COUNT) {
            createParticle(width, height, true)
        }
    }

    // 创建新粒子 - 修正生成位置和方向
    private fun createParticle(width: Int, height: Int, randomPosition: Boolean = false) {
        // 从底部从左到右随机位置生成
        val x = if (randomPosition) {
            random.nextFloat() * width
        } else {
            // 从底部边缘生成，从左到右随机
            random.nextFloat() * width
        }

        // 从屏幕底部生成
        val y = if (randomPosition) {
            random.nextFloat() * height
        } else {
            height.toFloat() // 从屏幕底部开始
        }

        // 计算发射方向 - 主要向上，带一些随机角度
        val baseAngle = -Math.PI.toFloat() / 2 // 向上方向（-90度）
        val randomAngle = (random.nextFloat() - 0.5f) * (PARTICLE_SPREAD_ANGLE * Math.PI / 180f)
        val angle = baseAngle + randomAngle

        val speed = random.nextFloat() * (PARTICLE_SPEED_MAX - PARTICLE_SPEED_MIN) + PARTICLE_SPEED_MIN

        val velocityX = cos(angle) * speed
        val velocityY = sin(angle) * speed * VERTICAL_SPEED_RATIO // 修正向上距离

        val size = random.nextFloat() * (PARTICLE_SIZE_MAX - PARTICLE_SIZE_MIN) + PARTICLE_SIZE_MIN
        val lifetime = random.nextFloat() * (PARTICLE_LIFETIME_MAX - PARTICLE_LIFETIME_MIN) + PARTICLE_LIFETIME_MIN

        // 使用当前mixColor作为基础颜色
        val baseColor = currentMixColor.copyOf()

        // 混合白色（增加亮度）
        val mixedColor = floatArrayOf(
            min(1.0f, baseColor[0] * MIX_COLOR + MIX_COLOR_R), // R
            min(1.0f, baseColor[1] * MIX_COLOR + MIX_COLOR_R), // G
            min(1.0f, baseColor[2] * MIX_COLOR + MIX_COLOR_R), // B
            baseColor[3] // Alpha
        )

        particles.add(Particle(
            x = x,
            y = y,
            velocityX = velocityX.toFloat(),
            velocityY = velocityY.toFloat(),
            size = size,
            lifetime = if (randomPosition) random.nextFloat() * lifetime else lifetime,
            maxLifetime = lifetime,
            currentColor = mixedColor
        ))
    }

    // 更新当前mixColor
    private fun updateCurrentMixColor(time: Float) {
        // 模拟着色器中的颜色混合逻辑
        val factor1 = (sin(time * COLOR_CHANGE_SPEED) + 1.0f) * 0.5f
        val factor2 = (sin(time * COLOR_CHANGE_SPEED + 2.094f) + 1.0f) * 0.5f
        val factor3 = (sin(time * COLOR_CHANGE_SPEED + 4.188f) + 1.0f) * 0.5f

        val sum = factor1 + factor2 + factor3
        val normalized1 = factor1 / sum
        val normalized2 = factor2 / sum
        val normalized3 = factor3 / sum

        // 混合三种颜色
        currentMixColor[0] = edgeColor1[0] * normalized1 + edgeColor2[0] * normalized2 + edgeColor3[0] * normalized3
        currentMixColor[1] = edgeColor1[1] * normalized1 + edgeColor2[1] * normalized2 + edgeColor3[1] * normalized3
        currentMixColor[2] = edgeColor1[2] * normalized1 + edgeColor2[2] * normalized2 + edgeColor3[2] * normalized3
        currentMixColor[3] = 1.0f
    }

    // 发射新粒子
    private fun emitParticles() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastEmissionTime > 16) { // 约60FPS
            for (i in 0 until PARTICLE_EMISSION_RATE) {
                if (particles.size < PARTICLE_COUNT) {
                    createParticle(width, height)
                }
            }
            lastEmissionTime = currentTime
        }
    }

    // 更新所有粒子 - 修正消失条件为内圈矩形边缘
    private fun updateParticles() {
        val particlesToRemove = mutableListOf<Particle>()

        // 第一步：更新现有粒子并标记需要移除的粒子
        for (particle in particles) {
            // 更新位置
            particle.x += particle.velocityX
            particle.y += particle.velocityY

            // 更新生命周期
            particle.lifetime -= 0.016f // 假设60FPS

            // 检查是否到达内圈矩形边缘 - 新的消失条件
            val reachedRectTop = particle.y <= rectY + rectHeight &&
                    particle.y >= rectY &&
                    particle.x >= rectX &&
                    particle.x <= rectX + rectWidth

            // 如果粒子超出屏幕、生命周期结束或到达内圈矩形边缘，标记为需要移除
            if (particle.lifetime <= 0 ||
                particle.y < -particle.size ||
                particle.y > height + particle.size ||
                particle.x < -particle.size ||
                particle.x > width + particle.size ||
                reachedRectTop) {
                particlesToRemove.add(particle)
            } else {
                // 更新粒子颜色（基于当前mixColor混合白色）
                particle.currentColor[0] = min(1.0f, currentMixColor[0] * MIX_COLOR + MIX_COLOR_R)
                particle.currentColor[1] = min(1.0f, currentMixColor[1] * MIX_COLOR + MIX_COLOR_R)
                particle.currentColor[2] = min(1.0f, currentMixColor[2] * MIX_COLOR + MIX_COLOR_R)

                // 根据生命周期调整alpha
                val lifeRatio = particle.lifetime / particle.maxLifetime
                particle.currentColor[3] = lifeRatio * 0.8f // 随着生命周期衰减
            }
        }

        // 第二步：移除标记的粒子
        particles.removeAll(particlesToRemove)

        // 第三步：添加新粒子以维持粒子数量
        val particlesNeeded = PARTICLE_COUNT - particles.size
        if (particlesNeeded > 0) {
            for (i in 0 until particlesNeeded) {
                createParticle(width, height)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        // 先绘制边缘光效果
        super.onDraw(canvas)

        // 然后绘制所有粒子
        for (particle in particles) {
            particlePaint.color = android.graphics.Color.argb(
                (particle.currentColor[3] * 255).toInt(),
                (particle.currentColor[0] * 255).toInt(),
                (particle.currentColor[1] * 255).toInt(),
                (particle.currentColor[2] * 255).toInt()
            )
            canvas.drawCircle(particle.x, particle.y, particle.size, particlePaint)
        }
    }

}