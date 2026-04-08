/*
* Created by allan on 2026/03/27.
*
* Copyright (C) 2026 [allan]. All Rights Reserved.
*
* This software is proprietary and confidential. Unauthorized use, copying,
* modification, or distribution is prohibited without prior written consent.
*
* For inquiries, contact: [contacts@allan]
*/

package com.au.audiorecordplayer.recorder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.toColorInt
import com.au.module_android.utils.dp

/**
 * 音频波形自定义 View
 * - 录音模式：最新样本在中线，更旧向左；左缘至簇左为点、中线右为点；仅左半幅存 deque
 * - 播放模式：与原版一致，追加柱从右向左，左侧为点占位
 */
class AudioWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        /**
         * 振幅 1.0 时波形柱相对「可用高度 (height - mMinBarHeight)」的倍率；
         * 略大于 1 可使最大音量更接近顶满 View 高度，可按观感微调。
         */
        const val WAVE_BAR_HEIGHT_RATIO = 1.25f
    }

    enum class Mode { IDLE, RECORDING, PLAYBACK }

    private var mMode = Mode.IDLE

    private val mAmplitudes = ArrayDeque<Float>()

    /** 播放复现：与录音时顺序一致的归一化振幅 */
    private var mReplaySamples = FloatArray(0)

    /** 播放进度（0f~1f），决定已「播出」的样本数 */
    private var mPlayProgress = 0f

    private val mBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#FF8C00".toColorInt()
        strokeCap = Paint.Cap.ROUND
        alpha = (255 * 0.8f).toInt()
    }

    private val mDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#FF8C00".toColorInt()
        alpha = (255 * 0.8f).toInt()
    }

    private val mBarWidth = 2f.dp
    private val mBarGap = 2f.dp
    private val mMinBarHeight = 2f.dp
    private val mDotSize = 2f.dp

    fun setMode(mode: Mode) {
        mMode = mode
        when (mode) {
            Mode.IDLE -> {
                mAmplitudes.clear()
                mReplaySamples = FloatArray(0)
                mPlayProgress = 0f
            }
            Mode.RECORDING -> {
                mReplaySamples = FloatArray(0)
                mPlayProgress = 0f
            }
            Mode.PLAYBACK -> {
                // 具体数据由 setPlaybackReplay 设置
            }
        }
        invalidate()
    }

    /**
     * 进入播放预览：使用录音阶段缓存的振幅列表，绘制逻辑与原版一致（点 + 从右向左柱）。
     */
    fun setPlaybackReplay(samples: List<Float>) {
        mAmplitudes.clear()
        mReplaySamples = samples.toFloatArray()
        mMode = Mode.PLAYBACK
        mPlayProgress = 0f
        invalidate()
    }

    /** 录音模式：追加一个振幅值 (0~32767) */
    fun addAmplitude(amplitude: Int) {
        if (mMode != Mode.RECORDING) return
        val normalized = (amplitude / 32767f).coerceIn(0f, 1f)
        val maxBars = maxBarsRecording()
        mAmplitudes.addLast(normalized)
        while (mAmplitudes.size > maxBars) mAmplitudes.removeFirst()
        invalidate()
    }

    /** 播放复现：更新进度 0~1，决定已展示的已播样本后缀长度 */
    fun updatePlayProgress(progress: Float) {
        if (mMode != Mode.PLAYBACK) return
        mPlayProgress = progress.coerceIn(0f, 1f)
        invalidate()
    }

    /** 冻结当前波形（从录音模式切换到暂停模式时调用） */
    fun freeze() {
        mMode = Mode.IDLE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        when (mMode) {
            Mode.IDLE -> {
                drawDotsLeftOfRecording(canvas, mAmplitudes.size)
                drawDotsRightOfCluster(canvas)
                drawRecordBarsFromCenterLeft(canvas, mAmplitudes.toList())
            }
            Mode.RECORDING -> {
                drawDotsLeftOfRecording(canvas, mAmplitudes.size)
                drawDotsRightOfCluster(canvas)
                drawRecordBarsFromCenterLeft(canvas, mAmplitudes.toList())
            }
            Mode.PLAYBACK -> drawPlaybackReplay(canvas)
        }
    }

    /** 中线 x（最新柱所在位置） */
    private fun centerLineX(): Float = width / 2f

    /**
     * 录音/空闲：左半幅可容纳的柱数（最新靠 width/2，向左递减）。
     */
    private fun maxBarsRecording(): Int {
        if (width == 0) return 50
        val centerX = centerLineX()
        val step = mBarWidth + mBarGap
        val barHalf = mBarWidth / 2f
        var count = 0
        var x = centerX
        while (x >= barHalf - 0.01f) {
            count++
            x -= step
        }
        return count.coerceAtLeast(1)
    }

    /** 录音/空闲：最旧柱以左到左缘为占位点（无柱时铺满至中线前） */
    private fun drawDotsLeftOfRecording(canvas: Canvas, barsCount: Int) {
        if (width == 0 || height == 0) return
        val centerX = centerLineX()
        val centerY = height / 2f
        val step = mBarWidth + mBarGap
        val dotRadius = mDotSize / 2f
        val oldestX = if (barsCount == 0) centerX else centerX - (barsCount - 1) * step
        val endExclusive = if (barsCount > 0) oldestX - step else centerX
        var dotX = mBarWidth / 2f
        while (dotX < endExclusive + 0.01f) {
            canvas.drawCircle(dotX, centerY, dotRadius, mDotPaint)
            dotX += step
        }
    }

    /** 录音/空闲：竖线中线以右为占位点（不含中线上的柱位） */
    private fun drawDotsRightOfCluster(canvas: Canvas) {
        if (width == 0 || height == 0) return
        val centerX = centerLineX()
        val centerY = height / 2f
        val step = mBarWidth + mBarGap
        val dotRadius = mDotSize / 2f
        val rightLimit = width - mBarWidth / 2f
        var dotX = centerX + step
        while (dotX <= rightLimit + 0.01f) {
            canvas.drawCircle(dotX, centerY, dotRadius, mDotPaint)
            dotX += step
        }
    }

    private fun drawDotsForBarCount(canvas: Canvas, maxBars: Int, barsCount: Int) {
        val centerY = height / 2f
        val step = mBarWidth + mBarGap
        val dotRadius = mDotSize / 2f
        val dotsCount = maxBars - barsCount
        for (i in 0 until dotsCount) {
            val x = i * step + mBarWidth / 2f
            canvas.drawCircle(x, centerY, dotRadius, mDotPaint)
        }
    }

    /** 按 [WAVE_BAR_HEIGHT_RATIO] 将振幅映射为柱高，并限制在 View 高度内 */
    private fun barHeightForAmplitude(amp: Float): Float {
        val usable = (height - mMinBarHeight).coerceAtLeast(0f)
        val h = mMinBarHeight + amp.coerceIn(0f, 1f) * usable * WAVE_BAR_HEIGHT_RATIO
        return h.coerceIn(mMinBarHeight, height.toFloat())
    }

    /**
     * amplitudes：时间序 [最旧 .. 最新]，最新柱绘制在 [centerLineX]。
     */
    private fun drawRecordBarsFromCenterLeft(canvas: Canvas, amplitudes: List<Float>) {
        if (width == 0 || height == 0 || amplitudes.isEmpty()) return
        val centerX = centerLineX()
        val centerY = height / 2f
        val step = mBarWidth + mBarGap
        mBarPaint.strokeWidth = mBarWidth
        val n = amplitudes.size
        for (i in amplitudes.indices) {
            val amp = amplitudes[i]
            val barHeight = barHeightForAmplitude(amp)
            mBarPaint.alpha = (80 + (amp * 175).toInt()).coerceIn(80, 255)
            val offsetFromNewest = n - 1 - i
            val x = centerX - offsetFromNewest * step
            canvas.drawLine(x, centerY - barHeight / 2f, x, centerY + barHeight / 2f, mBarPaint)
        }
    }

    /** 播放：与早期实现相同，柱紧贴视图右侧向内铺开 */
    private fun drawRecordBars(canvas: Canvas, amplitudes: List<Float>) {
        if (width == 0 || height == 0) return
        val centerY = height / 2f
        val step = mBarWidth + mBarGap
        val maxBars = maxVisibleBars()
        mBarPaint.strokeWidth = mBarWidth
        for (i in amplitudes.indices.reversed()) {
            val amp = amplitudes[i]
            val barHeight = barHeightForAmplitude(amp)
            mBarPaint.alpha = (80 + (amp * 175).toInt()).coerceIn(80, 255)
            val barIndex = maxBars - amplitudes.size + i
            val x = barIndex * step + mBarWidth / 2f
            canvas.drawLine(x, centerY - barHeight / 2f, x, centerY + barHeight / 2f, mBarPaint)
        }
    }

    /** 与早期播放一致：按进度展示已播样本的后缀窗口 */
    private fun drawPlaybackReplay(canvas: Canvas) {
        if (width == 0 || height == 0) return
        val maxBars = maxVisibleBars()
        val total = mReplaySamples.size
        if (total == 0) {
            drawDotsForBarCount(canvas, maxBars, 0)
            return
        }
        val endIdx = (mPlayProgress * total).toInt().coerceIn(0, total)
        val count = minOf(endIdx, maxBars)
        val startIdx = (endIdx - count).coerceAtLeast(0)
        val slice = mReplaySamples.slice(startIdx until endIdx)
        drawDotsForBarCount(canvas, maxBars, slice.size)
        drawRecordBars(canvas, slice)
    }

    private fun maxVisibleBars(): Int {
        if (width == 0) return 100
        return (width / (mBarWidth + mBarGap)).toInt() + 1
    }
}
