package com.au.audiorecordplayer.cam2.view.gl

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.SurfaceHolder
import com.au.audiorecordplayer.cam2.view.ICamRealView
import com.au.audiorecordplayer.cam2.view.IViewOnSurfaceCallback
import com.au.audiorecordplayer.util.MyLog

class CamGLSurfaceView : GLSurfaceView, ICamRealView {
    private var mCallback: IViewOnSurfaceCallback? = null
    lateinit var camRenderer: CameraV2Renderer

    override fun setCallback(cb: IViewOnSurfaceCallback) {
        this.mCallback = cb
    }

    fun getCallback(): IViewOnSurfaceCallback? {
        return mCallback
    }

    constructor(context: Context?) : super(context) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    private fun init() {
        setEGLContextClientVersion(2)

        val renderer = CameraV2Renderer(this)
        camRenderer = renderer
        setRenderer(renderer)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        MyLog.d("surfaceDestroyed")
        mCallback?.onSurfaceDestroyed()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        MyLog.d("onDetachedFromWindow")
    }
}