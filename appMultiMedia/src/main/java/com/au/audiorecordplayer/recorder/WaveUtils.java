package com.au.audiorecordplayer.recorder;

import android.util.Log;

/*
 * AudioUtils：音频处理工具类
 * 1. 计算 PCM 数据的 RMS
 * 2. 将 RMS 转为 dB
 */
public class WaveUtils {

    /**
     * 计算 PCM 16 位数据的 RMS
     */
    public static double calculateRMS(byte[] buffer, int readBytes) {
        long sumSquare = 0;
        int sampleCount = 0;

        for (int i = 0; i < readBytes - 1; i += 2) {
            short sample = (short) ((buffer[i] & 0xff) | (buffer[i + 1] << 8));
            sumSquare += sample * sample;
            sampleCount++;
        }

        if (sampleCount == 0) return 0;
        double meanSquare = sumSquare / (double) sampleCount;
        return Math.sqrt(meanSquare);
    }

    public static double rmsToDb(double rms) {
        if (rms <= 0) return -120; // 返回一个很小的dB值而不是0
        double db = 20 * Math.log10(rms / 32768.0);

        // 限制在合理范围内
        return Math.max(-120, db);
    }

    // 定义映射范围
    public static final float MIN_OUTPUT = 1.0f;
    public static final float MAX_OUTPUT = 1.2f;

    public static float mapRmsToRange(double rms) {
        // 定义RMS的合理范围（根据你的实际观察调整）
        final double MIN_RMS = 100;    // 安静环境
        final double MAX_RMS = 5000;   // 较大声音

        // 限制RMS在范围内
        double clampedRms = Math.max(MIN_RMS, Math.min(MAX_RMS, rms));

        // 线性映射
        double ratio = (clampedRms - MIN_RMS) / (MAX_RMS - MIN_RMS);
        return (float) (MIN_OUTPUT + ratio * (MAX_OUTPUT - MIN_OUTPUT));
    }
}