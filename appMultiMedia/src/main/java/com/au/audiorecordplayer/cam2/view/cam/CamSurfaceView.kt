package com.au.audiorecordplayer.cam2.view.cam

import android.content.Context
import android.util.AttributeSet
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.au.audiorecordplayer.cam2.view.ICamRealView
import com.au.audiorecordplayer.cam2.view.IViewOnSurfaceCallback
import com.au.audiorecordplayer.util.MyLog

class CamSurfaceView : SurfaceView, SurfaceHolder.Callback, ICamRealView<Surface> {
    private var mCallback: IViewOnSurfaceCallback<Surface>? = null

    override fun setCallback(cb: IViewOnSurfaceCallback<Surface>) {
        this.mCallback = cb
    }

    constructor(context: Context?) : super(context) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    private fun init() {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        MyLog.d("CamSurfaceView SurfaceCreated")
        mCallback?.onSurfaceCreated(holder.surface)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        MyLog.d("SurfaceChanged")
        mCallback?.onSurfaceChanged()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        MyLog.d("surfaceDestroyed")
        mCallback?.onSurfaceDestroyed()
    }
}
