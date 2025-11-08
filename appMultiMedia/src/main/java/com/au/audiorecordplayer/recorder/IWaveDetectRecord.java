package com.au.audiorecordplayer.recorder;

public interface IWaveDetectRecord {
    interface IWaveDetectCallback {
        /**
         * @param rms 返回的是16位PCM数据的RMS
         * @param db 大致能看出一般是-50左右，说话后是-20～-10大声。
         */
        void onWaveDetect(double rms, double db);
    }

    void setWaveDetectCallback(IWaveDetectCallback callback);
}