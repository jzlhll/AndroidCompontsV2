package com.au.audiorecordplayer.cam2.view.cam

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.view.TextureView
import android.view.TextureView.SurfaceTextureListener
import com.au.audiorecordplayer.cam2.view.ICamRealView
import com.au.audiorecordplayer.cam2.view.IViewOnSurfaceCallback
import com.au.audiorecordplayer.util.MyLog

class CamTextureView : TextureView, SurfaceTextureListener, ICamRealView<SurfaceTexture> {
    private var mCallback: IViewOnSurfaceCallback<SurfaceTexture>? = null

    override fun setCallback(cb: IViewOnSurfaceCallback<SurfaceTexture>) {
        this.mCallback = cb
    }

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    private fun init() {
        surfaceTextureListener = this
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        MyLog.d("SurfaceCreated")
        mCallback?.onSurfaceCreated(surface)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        MyLog.d("SurfaceChanged")
        mCallback?.onSurfaceChanged()
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        MyLog.d("surfaceDestroyed")
        mCallback?.onSurfaceDestroyed()
        return false
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
    }
}
