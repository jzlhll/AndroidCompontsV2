package com.au.audiorecordplayer.recorder.a2AudioRecord

import com.au.audiorecordplayer.recorder.WaveRmsDbSample
import com.au.audiorecordplayer.recorder.IWaveDetectRecord

class WavePcmAudioRecord : SimplePCMAudioRecord() {
    val waveUtils = WaveRmsDbSample()

    override fun processAudioData(buffer: ByteArray?, readBytes: Int) {
        waveUtils.processAudioData(buffer, readBytes)
    }

    fun setWaveDetectCallback(callback: IWaveDetectRecord.IWaveDetectCallback?) {
        waveUtils.setWaveDetectCallback(callback)
    }
}