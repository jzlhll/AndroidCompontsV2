package com.au.audiorecordplayer.cam2.impl

import android.os.HandlerThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.au.module_android.log.logdNoFile

class MyCamViewModel : ViewModel() {
    val camManager:MyCamManager

    private var mSubThread: HandlerThread? = null

    init {
        logdNoFile { "MyCam ViewModel init" }
        val subThread = HandlerThread("Camera-thread")
        mSubThread = subThread
        subThread.start()
        camManager = MyCamManager(viewModelScope, looper = subThread.looper)
    }

    fun close() {
        logdNoFile { "MyCam ViewModel close" }
        camManager.closeCameraDirectly(true)
        mSubThread?.quitSafely()
    }
}