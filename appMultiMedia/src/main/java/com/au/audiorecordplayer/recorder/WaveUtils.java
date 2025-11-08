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
        // 16 位 PCM，每两个字节构成一个样本（小端）
        for (int i = 0; i < readBytes; i += 2) {
            short sample = (short) ((buffer[i] & 0xff) | (buffer[i + 1] << 8));
            sumSquare += sample * sample;
        }
        double meanSquare = sumSquare / (readBytes / 2.0);
        return Math.sqrt(meanSquare);
    }

    /**
     * 将 RMS 转换为 dB，参考值取最大 16 位 PCM 值 32768
     */
    public static double rmsToDb(double rms) {
        if (rms <= 0) return 0;
        return 20 * Math.log10(rms / 32768.0);
    }
}