package com.au.audiorecordplayer.cam2.impl

import android.os.HandlerThread
import androidx.lifecycle.ViewModel
import com.au.module_android.utils.logdNoFile

class MyCamViewModel : ViewModel() {
    val camManager:MyCamManager

    private var mSubThread: HandlerThread? = null

    init {
        val subThread = HandlerThread("Camera-thread")
        mSubThread = subThread
        subThread.start()
        camManager = MyCamManager(looper = subThread.looper)
    }

    fun close() {
        logdNoFile { "MyCam ViewModel close" }
        camManager.closeCameraDirectly(true)
        mSubThread?.quitSafely()
    }
}