package com.au.audiorecordplayer.particle

interface IScreenEffect {
    /**
     * 设置是否在录制状态
     */
    fun setVoiceIsRecording(isRecording: Boolean)
    /**
     * 集成办法：
     *
     */
    fun onRmsUpdated(rms: Double, db: Double)
}