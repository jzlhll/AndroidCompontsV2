package com.au.audiorecordplayer.particle
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.RuntimeShader
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.annotation.RequiresApi
import com.au.audiorecordplayer.recorder.WaveRmsDbSample
import com.au.module_android.utils.logdNoFile

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
open class CombinedScreenEffectView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), IScreenEffect {

    companion object {
        // Wave 相关常量
        const val WAVE_MAX_ALPHA = 0.2f
        const val WAVE_MIN_ALPHA = 0.05f
        const val DEF_WAVE_DURATION = 2000L
        const val WAVE_TRAJECTORY_DURATION = 4000L

        const val WAVE_MAX_HEIGHT = 0.03
        const val WAVE_MIN_HEIGHT = 0.005
        const val WAVE_MIN_WIDTH = 1.0 / 3.0
        const val WAVE_MAX_WIDTH = 2.0 / 3.0
        const val WAVE_RATIO_NOT_RECORD = 0.1f

        // Screen Effect 相关常量
        private const val RECT_RATIO_HORZ = 0.85f
        private const val RECT_RATIO_VERT = 0.88f
        private const val RECT_RATIO_VERT_T = 0.42f
        private const val DEFAULT_CORNER_RADIUS = 48.0
        private const val ANIMATION_DURATION = 6000L
        const val COLOR_CHANGE_SPEED = 1.5f
        private const val MAX_ALPHA = 0.65
    }

    // 合并后的AGSL代码 - 优化了重复计算和变量使用
    private val shaderSrc = """
        uniform float4 edgeColor1;   // 边缘颜色1
        uniform float4 edgeColor2;   // 边缘颜色2  
        uniform float4 edgeColor3;   // 边缘颜色3
        uniform float4 rectProps;    // 圆角矩形参数: x, y, width, height
        uniform float edgeTime;      // 边缘光时间变量
        
        // Wave uniforms
        uniform float glWaveHeight;
        uniform float glWaveRatio;
        uniform float2 iResolution;
        uniform float parabolaProgress;
        uniform float horizontalProgress;
        
        // 圆角矩形 SDF 函数
        float roundedBoxSDF(vec2 p, vec2 b, float r) {
            vec2 d = abs(p) - b;
            return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - r;
        }
        
        vec4 mixEdgeColors(float time) {
            float factor1 = (sin(time * $COLOR_CHANGE_SPEED) + 1.0) * 0.5;
            float factor2 = (sin(time * $COLOR_CHANGE_SPEED + 2.094) + 1.0) * 0.5;
            float factor3 = (sin(time * $COLOR_CHANGE_SPEED + 4.188) + 1.0) * 0.5;
            
            float sum = factor1 + factor2 + factor3;
            factor1 /= sum;
            factor2 /= sum; 
            factor3 /= sum;
            
            vec4 mixedColor = edgeColor1 * factor1 + edgeColor2 * factor2 + edgeColor3 * factor3;
            mixedColor.rgb *= 1.2;
            mixedColor.rgb = clamp(mixedColor.rgb, 0.0, 1.0);
            
            return mixedColor;
        }
        
        vec4 calculateEdgeEffect(vec2 fragCoord, vec4 dynamicColor) {
            // 计算圆角矩形的中心点和半尺寸
            vec2 rectCenter = vec2(rectProps.x + rectProps.z * 0.5, rectProps.y + rectProps.w * 0.5);
            vec2 rectHalfSize = vec2(rectProps.z * 0.5, rectProps.w * 0.5);
            
            // 计算当前像素相对于圆角矩形中心的坐标
            vec2 relativePos = fragCoord - rectCenter;
            
            // 计算到圆角矩形的距离
            float distanceToRect = roundedBoxSDF(relativePos, rectHalfSize, $DEFAULT_CORNER_RADIUS);
        
            if (distanceToRect <= 0.0) {
                return vec4(0.0, 0.0, 0.0, 0.0);
            }
            
            // 使用传入的动态颜色
            float alpha = $MAX_ALPHA * min(distanceToRect / 150.0, 1.0);
            return vec4(dynamicColor.rgb * alpha, alpha);
        }
        
        vec4 calculateWaveEffect(vec2 fragCoord, vec4 dynamicColor) {
        float2 uv = fragCoord / iResolution;
        uv.y = 1.0 - uv.y;
        
        // 预计算常用值
        float currentHeight = $WAVE_MIN_HEIGHT + glWaveHeight * ($WAVE_MAX_HEIGHT - $WAVE_MIN_HEIGHT);
        float bottomWidth = $WAVE_MIN_WIDTH + (glWaveRatio / 2.0) * ($WAVE_MAX_WIDTH - $WAVE_MIN_WIDTH);
        float halfBottomWidth = bottomWidth / 2.0;
        
        // 抛物线轨迹计算
        float startX = $WAVE_MIN_WIDTH / 2.0;
        float endX = 1.0 - $WAVE_MIN_WIDTH / 2.0;
        float parabolaCenter = startX + horizontalProgress * (endX - startX);
        
        // 使用更高效的二次函数计算
        float progressOffset = parabolaProgress - 0.5;
        float heightFactor = -4.0 * progressOffset * progressOffset + 1.0;
        float parabolaYOffset = heightFactor * currentHeight;
        
        // 抛物线方程
        float a = -parabolaYOffset / (halfBottomWidth * halfBottomWidth);
        float parabolaY = a * (uv.x - parabolaCenter) * (uv.x - parabolaCenter) + parabolaYOffset;
        
        // 检查是否在抛物线区域内
        bool inParabola = uv.y <= parabolaYOffset && 
                         uv.x >= (parabolaCenter - halfBottomWidth) && 
                         uv.x <= (parabolaCenter + halfBottomWidth) &&
                         uv.y <= parabolaY;
        
        if (inParabola) {
            float relativeHeight = uv.y / parabolaYOffset;
            float alpha = $WAVE_MIN_ALPHA + ($WAVE_MAX_ALPHA - $WAVE_MIN_ALPHA) * (1.0 - relativeHeight);
            
            // 边缘抗锯齿
            float distanceToEdge = parabolaY - uv.y;
            float edgeSmoothRange = 2.5 / iResolution.y;
            float edgeAlpha = smoothstep(0.0, edgeSmoothRange, distanceToEdge);
            
            float finalAlpha = alpha * edgeAlpha;
            
            // 优化：使用动态颜色与白色混合，提高wave可见度
            // 使用屏幕混合模式：1.0 - (1.0 - a) * (1.0 - b)，使颜色更亮
            vec3 white = vec3(1.0);
            vec3 waveRGB = 1.0 - (1.0 - dynamicColor.rgb) * (1.0 - white * 0.7);
            
            // 进一步增强wave的亮度
            waveRGB = waveRGB * 1.5;
            waveRGB = clamp(waveRGB, 0.0, 1.0);
            
            return vec4(waveRGB * finalAlpha, finalAlpha);
        } else {
            return vec4(0.0, 0.0, 0.0, 0.0);
        }
    }
    
    half4 main(float2 fragCoord) {
        // 共用颜色计算
        vec4 dynamicColor = mixEdgeColors(edgeTime);
        
        // 先计算边缘光效果
        vec4 edgeEffect = calculateEdgeEffect(fragCoord, dynamicColor);
        
        // 再计算波浪效果
        vec4 waveEffect = calculateWaveEffect(fragCoord, dynamicColor);
        
        // 使用预乘Alpha混合
        // 公式: result = foreground + background * (1 - foreground_alpha)
        vec3 mixedRGB = waveEffect.rgb + edgeEffect.rgb * (1.0 - waveEffect.a);
        float mixedAlpha = waveEffect.a + edgeEffect.a * (1.0 - waveEffect.a);
        
        return half4(mixedRGB, mixedAlpha);
    }
    """.trimIndent()

    // 统一的RuntimeShader和Paint
    private val runtimeShader: RuntimeShader = RuntimeShader(shaderSrc)
    private val paint = Paint()

    // Wave相关变量
    private var internalWaveHeight: Float = 0f
    @Volatile
    private var isRecording = false
    private var waveRatio: Float = WAVE_RATIO_NOT_RECORD
    private var currentWaveRatio: Float = WAVE_RATIO_NOT_RECORD

    // 抛物线动画变量
    private var parabolaProgress: Float = 0f
    private var horizontalProgress: Float = 0f

    // Screen Effect相关变量
    var rectX = 0f
    var rectY = 0f
    var rectWidth = 0f
    var rectHeight = 0f
    private var adjustSizeRatio = 1.05f

    // 颜色配置
    val edgeColor1 = floatArrayOf(0.2f, 0.2f, 1.0f, 1.0f)
    val edgeColor2 = floatArrayOf(0.0f, 0.8f, 1.0f, 1.0f)
    val edgeColor3 = floatArrayOf(0.7f, 0.2f, 1.0f, 1.0f)

    // 三个独立的动画器
    private var waveAnimator: ValueAnimator? = null
    private var trajectoryAnimator: ValueAnimator? = null
    private var edgeAnimator: ValueAnimator? = null

    init {
        paint.shader = runtimeShader
        setBackgroundColor(0x00000000)
        initializeShaders()
    }

    private fun initializeShaders() {
        runtimeShader.setFloatUniform("edgeColor1", edgeColor1)
        runtimeShader.setFloatUniform("edgeColor2", edgeColor2)
        runtimeShader.setFloatUniform("edgeColor3", edgeColor3)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        runtimeShader.setFloatUniform("iResolution", w.toFloat(), h.toFloat())
        updateRectProps(w, h)
    }

    private fun updateRectProps(w: Int, h: Int) {
        rectWidth = w * RECT_RATIO_HORZ / adjustSizeRatio
        rectHeight = h * RECT_RATIO_VERT / adjustSizeRatio
        rectX = (w - rectWidth) * 0.5f
        rectY = (h - rectHeight) * RECT_RATIO_VERT_T
        runtimeShader.setFloatUniform("rectProps", rectX, rectY, rectWidth, rectHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val finalWaveHeight = internalWaveHeight * currentWaveRatio
        runtimeShader.setFloatUniform("glWaveHeight", finalWaveHeight)
        runtimeShader.setFloatUniform("glWaveRatio", currentWaveRatio)
        runtimeShader.setFloatUniform("parabolaProgress", parabolaProgress)
        runtimeShader.setFloatUniform("horizontalProgress", horizontalProgress)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    private fun startWaveAnimation() {
        waveAnimator?.cancel()

        waveAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = DEF_WAVE_DURATION
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE

            addUpdateListener { animation ->
                internalWaveHeight = animation.animatedValue as Float
                // 在接近最小值时更新waveRatio，但避免重复赋值
                if (internalWaveHeight < 0.01f) {
                    currentWaveRatio = waveRatio
                    logdNoFile { "Wave ratio updated to: $currentWaveRatio" }
                }
                invalidate()
            }

            start()
        }
    }

    private fun startTrajectoryAnimation() {
        trajectoryAnimator?.cancel()

        trajectoryAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = WAVE_TRAJECTORY_DURATION
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART

            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                horizontalProgress = progress
                parabolaProgress = progress
                invalidate()
            }

            start()
        }
    }

    private fun startEdgeAnimation() {
        edgeAnimator?.cancel()

        edgeAnimator = ValueAnimator.ofFloat(0f, 6.283185f).apply {
            duration = ANIMATION_DURATION
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()

            addUpdateListener { animation ->
                val timeValue = animation.animatedValue as Float
                onValueAnimatorUpdate(timeValue)
                invalidate()
            }
            start()
        }
    }

    protected open fun onValueAnimatorUpdate(timeValue: Float) {
        // 更新着色器中的时间uniform
        runtimeShader.setFloatUniform("edgeTime", timeValue)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startWaveAnimation()
        startTrajectoryAnimation()
        startEdgeAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        waveAnimator?.cancel()
        trajectoryAnimator?.cancel()
        edgeAnimator?.cancel()
    }

    // IScreenEffect接口实现
    override fun setVoiceIsRecording(isRecording: Boolean) {
        this.isRecording = isRecording
        waveRatio = if (isRecording) {
            WaveRmsDbSample.DB_MAPPING_MIN
        } else {
            WAVE_RATIO_NOT_RECORD
        }
        logdNoFile { "set voice is Recording $currentWaveRatio to $waveRatio" }
    }

    override fun onVoiceDbUpdated(db: Double) {
        logdNoFile { "onVoiceUpdate db $db" }
        if (!isRecording) {
            waveRatio = WAVE_RATIO_NOT_RECORD
            return
        }
        val mapping = WaveRmsDbSample.dbMapping(db)
        waveRatio = mapping
        logdNoFile { "onVoiceDb Updated $currentWaveRatio to $waveRatio mapping $mapping" }
    }
}