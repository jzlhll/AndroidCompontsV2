package com.au.audiorecordplayer.recorder

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/*
 * AudioUtils：音频处理工具类
 * 1. 计算 PCM 数据的 RMS
 * 2. 将 RMS 转为 dB
 * 范围约束返回结果
 */
class WaveRmsDbSample : IWaveDetectRecord {
    companion object {
        private const val SAMPLE_INTERVAL_MS = 30L // 每30ms抽样一次
        private const val CALLBACK_INTERVAL_MS = 300L // 每300ms回调一次
        private const val MIN_SIGNIFICANT_CHANGE = 0.15 // 15%变化才认为是显著变化

        // 定义映射范围
        const val DB_MAPPING_NOT = 0.15f  //必须小于DB_MAPPING_MIN
        const val DB_MAPPING_MIN: Float = 0.2f //必须大于0.2
        const val DB_MAPPING_MAX: Float = 1.2f
        // 定义分贝的合理范围（根据实际观察调整）
        const val MIN_DB = -120.0 // 安静环境

        fun dbMapping(db: Double): Float { // 分段线性映射
            return when {
                db < -50 -> DB_MAPPING_MIN // 非常小的声音
                db < -38 -> DB_MAPPING_MIN + 0.2f // 小声音
                db < -32 -> DB_MAPPING_MIN + 0.3f // 小声音
                db < -28 -> DB_MAPPING_MIN + 0.4f // 小声音
                db < -22 -> DB_MAPPING_MIN + 0.5f // 中等声音
                db < -5 -> DB_MAPPING_MIN + 0.8f  // 较大声音
                else -> DB_MAPPING_MAX             // 很大声音
            }
        }

        fun rmsToDb(rms: Double): Double {
            if (rms <= 0) return MIN_DB // 返回一个很小的dB值而不是0

            val db = 20 * log10(rms / 32768.0)

            // 限制在合理范围内
            return max(MIN_DB, db)
        }
    }

    private var mLastCallbackRms = 0.0
    private var mLastCallbackTime = 0L
    private val mSampleBuffer = mutableListOf<Double>()
    private var mWaveDetectCallback: IWaveDetectRecord.IWaveDetectCallback? = null

    fun reset() {
        mLastCallbackRms = 0.0
        mLastCallbackTime = 0L
        mSampleBuffer.clear()
    }

    override fun setWaveDetectCallback(callback: IWaveDetectRecord.IWaveDetectCallback?) {
        mWaveDetectCallback = callback
    }

    override fun processAudioData(buffer: ByteArray?, readBytes: Int) {
        if (buffer == null || readBytes <= 0) return
        val currentTime = System.currentTimeMillis()
        if (currentTime - mLastCallbackTime < SAMPLE_INTERVAL_MS && mSampleBuffer.isNotEmpty()) {
            return
        }
        // 计算当前RMS
        val currentRms = calculateCurrentRMS(buffer, readBytes)
        if (currentRms > 0) {
            mSampleBuffer.add(currentRms)
        }

        // 检查是否达到回调间隔
        if (currentTime - mLastCallbackTime >= CALLBACK_INTERVAL_MS && mSampleBuffer.isNotEmpty()) {
            processCallback()
            mLastCallbackTime = currentTime
        }
    }

    private fun calculateCurrentRMS(buffer: ByteArray, readBytes: Int): Double {
        var sumSquare: Long = 0
        var sampleCount = 0

        var i = 0
        while (i < readBytes - 1) {
            val sample = ((buffer[i].toInt() and 0xff) or (buffer[i + 1].toInt() shl 8)).toShort()
            sumSquare += (sample * sample).toLong()
            sampleCount++
            i += 2
        }

        if (sampleCount == 0) return 0.0

        val meanSquare = sumSquare / sampleCount.toDouble()
        return sqrt(meanSquare)
    }

    private fun processCallback() {
        if (mSampleBuffer.isEmpty()) return

        // 计算样本平均值
        val averageRms = mSampleBuffer.average()

        // 检查是否有显著变化
        val shouldCallback = shouldTriggerCallback(averageRms)

        if (shouldCallback) {
            mWaveDetectCallback?.onWaveDetect(rmsToDb(averageRms))
            mLastCallbackRms = averageRms
        }

        // 清空样本缓冲区，保留最近几个样本以避免完全清空时的突变
        if (mSampleBuffer.size > 5) {
            // 保留最近5个样本，这样在下一次回调时不会完全从零开始
            val keepCount = min(5, mSampleBuffer.size)
            val recentSamples = mSampleBuffer.takeLast(keepCount)
            mSampleBuffer.clear()
            mSampleBuffer.addAll(recentSamples)
        } else {
            mSampleBuffer.clear()
        }
    }

    private fun shouldTriggerCallback(currentRms: Double): Boolean {
        // 初始状态总是回调
        if (mLastCallbackRms == 0.0) {
            return true
        }

        // 计算相对变化率
        val changeRate = abs(currentRms - mLastCallbackRms) / mLastCallbackRms

        // 对于小RMS值，使用绝对阈值
        if (mLastCallbackRms < 200) {
            val absoluteChange = abs(currentRms - mLastCallbackRms)
            return absoluteChange > 50 // 绝对变化超过50才回调
        }

        // 只有变化超过阈值才回调
        return changeRate > MIN_SIGNIFICANT_CHANGE
    }
}