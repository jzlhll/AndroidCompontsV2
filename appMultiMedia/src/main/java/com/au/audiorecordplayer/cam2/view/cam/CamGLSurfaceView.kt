package com.au.audiorecordplayer.cam2.view.cam

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.SurfaceHolder
import com.au.audiorecordplayer.cam2.gl.CameraRenderer
import com.au.audiorecordplayer.cam2.view.ICamRealView
import com.au.audiorecordplayer.cam2.view.IViewOnSurfaceCallback
import com.au.audiorecordplayer.util.MyLog
import com.au.module_android.utils.logdNoFile

class CamGLSurfaceView : GLSurfaceView, SurfaceHolder.Callback, ICamRealView<SurfaceTexture> {
    private var mCallback: IViewOnSurfaceCallback<SurfaceTexture>? = null
    private var mCamRenderer:CameraRenderer? = null

    //接收相机数据的纹理
    val textureIds = intArrayOf(0)
    val textureId: Int
        get() = textureIds[0]
    //接收相机数据的 SurfaceTexture
    private var mSurfaceTexture: SurfaceTexture? = null
    fun getSurfaceTextureForce(): SurfaceTexture {
        return mSurfaceTexture!!
    }

    fun setSurfaceTexture(surfaceTexture: SurfaceTexture) {
        mSurfaceTexture = surfaceTexture
    }

    /**
     * @param renderer
     * @param version GL的版本，默认3。可选2.
     */
    fun initGL(renderer: CameraRenderer, version:Int = 3) {
        logdNoFile { "init GL renderer" }
        this.mCamRenderer = renderer
        setEGLContextClientVersion(version)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY //必须在设置renderer之后
    }

    override fun setCallback(cb: IViewOnSurfaceCallback<SurfaceTexture>) {
        this.mCallback = cb
    }

    fun getCallback(): IViewOnSurfaceCallback<SurfaceTexture>? {
        return mCallback
    }

    constructor(context: Context?) : super(context) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    private fun init() {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        logdNoFile { "surfaceCreated" }
       // mCallback?.onSurfaceCreated()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        MyLog.d("SurfaceChanged")
        //mCallback?.onSurfaceChanged()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        MyLog.d("surfaceDestroyed")
        mCallback?.onSurfaceDestroyed()
    }
}
