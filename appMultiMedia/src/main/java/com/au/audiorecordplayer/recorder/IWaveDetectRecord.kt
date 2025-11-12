package com.au.audiorecordplayer.recorder

interface IWaveDetectRecord {
    interface IWaveDetectCallback {
        /**
         * @param db 大致能看出一般是-50左右，说话后是-20～-10大声。
         */
        fun onWaveDetect(db: Double)
    }

    fun setWaveDetectCallback(callback: IWaveDetectCallback?)
    fun processAudioData(buffer: ByteArray?, readBytes: Int)
}