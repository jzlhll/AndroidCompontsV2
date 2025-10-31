package com.au.audiorecordplayer.cam2

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.ImageView
import androidx.fragment.app.Fragment
import com.au.module_android.utils.gone
import com.au.module_android.utils.visible

class Camera2FragmentRecord(private val f: Fragment,
                            private val timeTv: android.widget.TextView,
                            private val recordBtn: ImageView) {
    var isRecording: Boolean = false

    private var mTimeSec = 0
    private var mRunnableIndex = 0
    private var mRunnableLastTime = 0L

    private val handler = Handler(Looper.getMainLooper())

    fun onRecordEnd() {
        recordBtn.imageTintList = ColorStateList.valueOf(Color.WHITE)
        isRecording = false
        recordBtn.gone()
        handler.removeCallbacks(timeUpdateRunnable)
        mTimeSec = 0
    }

    fun onRecordStart() {
        recordBtn.imageTintList = ColorStateList.valueOf(Color.RED)
        recordBtn.visible()
        isRecording = true
        mRunnableLastTime = 0
        mRunnableIndex = 0
        handler.post(timeUpdateRunnable)
    }

    private val timeUpdateRunnable: Runnable = object : Runnable {
        override fun run() {
            if (!f.isResumed) {
                return
            }

            if (mRunnableLastTime == 0L) {
                mRunnableLastTime = SystemClock.elapsedRealtime()
            }
            timeTv.text = String.format("· %d", mTimeSec++)
            var delayTime: Long = 1000
            if (mRunnableIndex++ == 10) {
                mRunnableIndex = 0 //每10s 修正delay的时间
                val cur = SystemClock.elapsedRealtime()
                delayTime = 1000 - (cur - 10000 - mRunnableLastTime)
                if (delayTime < 0) {
                    delayTime = 0
                }
                mRunnableLastTime = cur
            }
            handler.postDelayed(timeUpdateRunnable, delayTime)
        }
    }
}