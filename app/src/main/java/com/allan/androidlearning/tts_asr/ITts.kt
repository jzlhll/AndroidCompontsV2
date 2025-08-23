package com.allan.androidlearning.tts_asr

import androidx.annotation.WorkerThread

interface ITts {
    fun init()
    fun stop()
    fun destroy()

    @WorkerThread
    fun speak(text: String)

    fun setOnDoneCallback(cb:()->Unit)
}