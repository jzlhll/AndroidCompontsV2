package com.au.audiorecordplayer.particle

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.RuntimeShader
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.annotation.RequiresApi
import android.os.Build
import com.au.audiorecordplayer.recorder.WaveRmsDbSample
import com.au.module_android.utils.logdNoFile
import kotlin.math.max
import kotlin.math.min

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class WaveParabolaView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), IScreenEffect {

    companion object {
        const val WAVE_MAX_ALPHA = 0.3f
        const val WAVE_MIN_ALPHA = 0.05f
        const val DEF_WAVE_DURATION = 2000L // 2秒

        const val WAVE_MAX_HEIGHT = 0.03
        const val WAVE_MIN_HEIGHT = 0.005

        // 底部宽度范围：从1/3到1/2
        const val WAVE_MIN_WIDTH = 1.0 / 3.0
        const val WAVE_MAX_WIDTH = 1.0 / 2.0

        const val WAVE_RATIO_NOT_RECORD = 0.2f
    }

    private val agslSource = """
        uniform float waveHeight;
        uniform float waveRatio;
        uniform float2 iResolution;
        uniform float maxAlpha;
        uniform float minAlpha;
        uniform float parabolaProgress; // 控制抛物线运动进度
        uniform float horizontalProgress; // 控制水平移动进度
        
        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / iResolution;
            uv.y = 1.0 - uv.y;
            
            float currentHeight = $WAVE_MIN_HEIGHT + waveHeight * ($WAVE_MAX_HEIGHT - $WAVE_MIN_HEIGHT);
            
            float bottomWidth = $WAVE_MIN_WIDTH + (waveRatio / 2.0) * ($WAVE_MAX_WIDTH - $WAVE_MIN_WIDTH);
            float halfBottomWidth = bottomWidth / 2.0;
            
            // 抛物线轨迹：从底部1/3位置开始，向上向右运动，到达顶点后向右下运动
            // 水平移动：从左侧到右侧
            float startX = $WAVE_MIN_WIDTH / 2.0; // 起始位置：1/3宽度处
            float endX = 1.0 - $WAVE_MIN_WIDTH / 2.0; // 结束位置
            float parabolaCenter = startX + horizontalProgress * (endX - startX);
            
            // 抛物线高度变化：先上升后下降
            // 使用二次函数模拟抛体运动轨迹
            float heightFactor = -4.0 * (parabolaProgress - 0.5) * (parabolaProgress - 0.5) + 1.0;
            float parabolaYOffset = heightFactor * currentHeight;
            
            // 抛物线方程，基于当前中心位置和高度偏移
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
                
                // 在抛物线顶部边缘添加抗锯齿
                float distanceToEdge = parabolaY - uv.y;
                float edgeSmoothRange = 2.5 / iResolution.y;
                float edgeAlpha = smoothstep(0.0, edgeSmoothRange, distanceToEdge);
                
                // 最终alpha是基础alpha乘以边缘平滑因子
                float finalAlpha = alpha * edgeAlpha;
                
                // 重要：使用预乘Alpha - RGB通道乘以alpha值
                return half4(1.0 * finalAlpha, 1.0 * finalAlpha, 0.0 * finalAlpha, finalAlpha);
            } else {
                return half4(0.0, 0.0, 0.0, 0.0);
            }
        }
    """.trimIndent()

    // 创建RuntimeShader
    private val runtimeShader: RuntimeShader = RuntimeShader(agslSource)
    private val paint = Paint()
    private var waveAnimator: ValueAnimator? = null

    private var internalWaveHeight: Float = 0f

    @Volatile
    private var mIsRecording = false
    // 外部传入的waveRatio加成
    private var waveRatio: Float = WAVE_RATIO_NOT_RECORD
    private var currentWaveRatio: Float = WAVE_RATIO_NOT_RECORD

    // 动画速度控制
    private var animationDuration: Long = DEF_WAVE_DURATION

    private var trajectoryAnimator: ValueAnimator? = null
    private var parabolaProgress: Float = 0f // 控制抛物线高度变化
    private var horizontalProgress: Float = 0f // 控制水平移动

    init {
        paint.shader = runtimeShader
        setBackgroundColor(0xFF000000.toInt()) // 黑色背景
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // 更新分辨率uniform
        runtimeShader.setFloatUniform("iResolution", w.toFloat(), h.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val finalWaveHeight = internalWaveHeight * currentWaveRatio
        runtimeShader.setFloatUniform("waveHeight", finalWaveHeight)
        runtimeShader.setFloatUniform("waveRatio", currentWaveRatio)
        runtimeShader.setFloatUniform("parabolaProgress", parabolaProgress)
        runtimeShader.setFloatUniform("horizontalProgress", horizontalProgress)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    private fun startWaveAnimation() {
        waveAnimator?.cancel()

        waveAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = animationDuration
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE

            addUpdateListener { animation ->
                val v = animation.animatedValue as Float
                internalWaveHeight = v
                if (v == 0f) {
                    logdNoFile { "change to currentWave $waveRatio" }
                    currentWaveRatio = waveRatio
                }
                invalidate()
            }
            start()
        }
    }

    // 新增：启动抛物线轨迹动画
    private fun startTrajectoryAnimation() {
        trajectoryAnimator?.cancel()

        trajectoryAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 4000L // 完整的抛物线轨迹周期
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART

            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float

                // 水平移动：从0到1匀速移动
                horizontalProgress = progress

                // 抛物线高度：先上升后下降
                // 使用正弦函数模拟抛体运动
                parabolaProgress = progress

                invalidate()
            }
            start()
        }
    }


    // 清理资源
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        waveAnimator?.cancel()
        trajectoryAnimator?.cancel() // 新增：取消轨迹动画
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startWaveAnimation()
        startTrajectoryAnimation() // 新增：启动轨迹动画
    }

    override fun setVoiceIsRecording(isRecording: Boolean) {
        mIsRecording = isRecording
        waveRatio = if (isRecording) {
            WaveRmsDbSample.DB_MAPPING_MIN
        } else {
            WAVE_RATIO_NOT_RECORD
        }
    }

    override fun onVoiceDbUpdated(db: Double) {
        if (!mIsRecording) {
            waveRatio = WAVE_RATIO_NOT_RECORD
            return
        }
        val mapping = WaveRmsDbSample.dbMapping(db)
        logdNoFile { "update wave mapping $mapping" }
        waveRatio = max(WaveRmsDbSample.DB_MAPPING_MIN, min(WaveRmsDbSample.DB_MAPPING_MAX, mapping))
    }
}