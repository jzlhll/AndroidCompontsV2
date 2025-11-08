package com.au.audiorecordplayer.particle

interface IScreenEffect {
    fun onVoiceStarted()
    fun onVoiceStopped()
    fun onRmsUpdated(rms: Double)
}