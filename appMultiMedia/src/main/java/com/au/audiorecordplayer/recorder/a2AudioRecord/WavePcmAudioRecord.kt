package com.au.audiorecordplayer.recorder.a2AudioRecord

import com.au.audiorecordplayer.recorder.WaveUtils
import com.au.audiorecordplayer.recorder.IWaveDetectRecord

class WavePcmAudioRecord : SimplePCMAudioRecord(), IWaveDetectRecord {
    private var mWaveDetectCallback: IWaveDetectRecord.IWaveDetectCallback? = null

    override fun processAudioData(data: ByteArray?, length: Int) {
        val rms = WaveUtils.calculateRMS(data, length)
        mWaveDetectCallback?.onWaveDetect(rms, WaveUtils.rmsToDb(rms))
    }

    override fun setWaveDetectCallback(callback: IWaveDetectRecord.IWaveDetectCallback?) {
        mWaveDetectCallback = callback
    }

}