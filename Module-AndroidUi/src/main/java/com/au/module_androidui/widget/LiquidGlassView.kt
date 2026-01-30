package com.au.module_androidui.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.util.SizeF
import android.view.View
import androidx.annotation.RequiresApi
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt

data class GlassElement(
    val position: PointF,
    val size: SizeF,
    val scale: Float,
    val cornerRadius: Float,
    val elevation: Float,
    val centerDistortion: Float,
    val tint: Color,
    val darkness: Float,
    val warpEdges: Float,
    val blur: Float
)

class LiquidGlassView : View {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    private val paint = Paint()

    private val impl: ILiquidGlass

    init {
        impl = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            AndroidTLiquidGlass()
        } else {
            LowerLiquidGlass()
        }
    }

    private abstract class ILiquidGlass {
        abstract fun onElementsChanged()
        abstract fun applyRenderEffectTo(target: View)
        abstract fun onDraw(canvas: Canvas)
    }

    private inner class LowerLiquidGlass : ILiquidGlass() {
        private val nightColor = "#80333333".toColorInt()
        private val dayColor = "#80ffffff".toColorInt()

        private fun isNightMode(): Boolean {
            val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return mode == Configuration.UI_MODE_NIGHT_YES
        }

        override fun onElementsChanged() {
            invalidate()
        }

        override fun applyRenderEffectTo(target: View) {
            // 低版本不支持 RenderEffect，忽略
        }

        override fun onDraw(canvas: Canvas) {
            val isNight = isNightMode()
            val color = if (isNight) nightColor else dayColor
            canvas.drawColor(color)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private inner class AndroidTLiquidGlass : ILiquidGlass() {
        private val runtimeShader: RuntimeShader
        private var renderEffectTarget: View? = null

        private val maxElements = 10
        private val positions = FloatArray(maxElements * 2)
        private val sizes = FloatArray(maxElements * 2)
        private val scales = FloatArray(maxElements)
        private val radii = FloatArray(maxElements)
        private val elevations = FloatArray(maxElements)
        private val centerDistortions = FloatArray(maxElements)
        private val tints = FloatArray(maxElements * 4)
        private val darkness = FloatArray(maxElements)
        private val warpEdges = FloatArray(maxElements)
        private val blurs = FloatArray(maxElements)

        private val defaultShader: Shader by lazy {
            val bitmap = createBitmap(1, 1)
            bitmap.eraseColor(Color.TRANSPARENT)
            BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }

        init {
            val shaderCode = AndroidTLiquidGlassUtil2().liquidGlassView2
            runtimeShader = RuntimeShader(shaderCode)
        }

        override fun onElementsChanged() {
            renderEffectTarget?.let { target ->
                updateShaderUniforms(runtimeShader, target.width.toFloat(), target.height.toFloat())
                target.invalidate()
            } ?: run {
                invalidate()
            }
        }

        override fun applyRenderEffectTo(target: View) {
            renderEffectTarget = target
            
            // 初始更新
            updateShaderUniforms(runtimeShader, target.width.toFloat(), target.height.toFloat())

            val effect = android.graphics.RenderEffect.createRuntimeShaderEffect(runtimeShader, "contents")
            target.setRenderEffect(effect)
            
            // 监听器：如果视图大小改变则更新分辨率
            target.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                    updateShaderUniforms(runtimeShader, (right - left).toFloat(), (bottom - top).toFloat())
                    v.invalidate()
                }
            }
        }

        override fun onDraw(canvas: Canvas) {
            updateShaderUniforms(runtimeShader, width.toFloat(), height.toFloat())

            // 设置内容 Shader（仅在手动 onDraw 时需要，RenderEffect 会自动处理）
            val currentContentShader = contentShader ?: defaultShader
            runtimeShader.setInputShader("contents", currentContentShader)

            paint.shader = runtimeShader
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }

        /**
         * 更新 Shader 的 uniform 参数
         * @param w 绘制区域的宽度
         * @param h 绘制区域的高度
         */
        private fun updateShaderUniforms(shader: RuntimeShader, w: Float, h: Float) {
            // 设置分辨率
            shader.setFloatUniform("resolution", w, h)

            val elementsCount = minOf(elements.size, maxElements)
            shader.setIntUniform("elementsCount", elementsCount)

            for (i in 0 until elementsCount) {
                val element = elements[i]
                positions[i * 2] = element.position.x
                positions[i * 2 + 1] = element.position.y
                sizes[i * 2] = element.size.width
                sizes[i * 2 + 1] = element.size.height
                scales[i] = element.scale
                radii[i] = element.cornerRadius
                elevations[i] = element.elevation
                centerDistortions[i] = element.centerDistortion

                tints[i * 4] = element.tint.red()
                tints[i * 4 + 1] = element.tint.green()
                tints[i * 4 + 2] = element.tint.blue()
                tints[i * 4 + 3] = element.tint.alpha()

                darkness[i] = element.darkness
                warpEdges[i] = element.warpEdges
                blurs[i] = element.blur
            }

            shader.setFloatUniform("glassPositions", positions)
            shader.setFloatUniform("glassSizes", sizes)
            shader.setFloatUniform("glassScales", scales)
            shader.setFloatUniform("cornerRadii", radii)
            shader.setFloatUniform("elevations", elevations)
            shader.setFloatUniform("centerDistortions", centerDistortions)
            shader.setFloatUniform("glassTints", tints)
            shader.setFloatUniform("glassDarkness", darkness)
            shader.setFloatUniform("glassWarpEdges", warpEdges)
            shader.setFloatUniform("glassBlurs", blurs)
        }
    }

    var elements: List<GlassElement> = emptyList()
        set(value) {
            field = value
            impl.onElementsChanged()
        }

    // 默认背景内容 Shader（透明）
    var contentShader: Shader? = null
        set(value) {
            field = value
            invalidate()
        }

    /**
     * 将此液体玻璃效果应用于目标 View，使用 RenderEffect (API 33+)。
     * 这比 onDraw 更高效，并且会自动处理内容。
     */
    fun applyRenderEffectTo(target: View) {
        impl.applyRenderEffectTo(target)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        impl.onDraw(canvas)
    }

    /**
     * 创建一个填满 View 的圆角矩形（自动计算最大圆角，呈胶囊形或圆形）。
     */
    fun setupFullCapsule() {
        post {
            if (width > 0 && height > 0) {
                val radius = minOf(width, height) / 2f
                setupFullRoundedRect(radius)
            }
        }
    }

    /**
     * 创建一个填满 View 的圆角矩形，指定圆角半径。
     */
    fun setupFullRoundedRect(cornerRadius: Float) {
        post {
            if (width > 0 && height > 0) {
                val element = GlassElement(
                    position = PointF(0f, 0f),
                    size = SizeF(width.toFloat(), height.toFloat()),
                    scale = 1.0f,
                    cornerRadius = cornerRadius,
                    elevation = 0f, // 默认平面
                    centerDistortion = 0f,
                    tint = Color.valueOf(1f, 1f, 1f, 0.2f), // 默认微白透明
                    darkness = 0f,
                    warpEdges = 0f,
                    blur = 0.5f // 默认模糊
                )
                this.elements = listOf(element)
            }
        }
    }
}