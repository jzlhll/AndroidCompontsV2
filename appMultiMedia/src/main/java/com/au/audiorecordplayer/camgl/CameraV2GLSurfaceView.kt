package com.au.audiorecordplayer.camgl

import android.content.Context
import android.opengl.GLSurfaceView

/**
 * Created by lb6905 on 2017/7/19.
 */
class CameraV2GLSurfaceView(context: Context?) : GLSurfaceView(context) {
    val TAG: String = "Filter_CameraV2GLSurfaceView"
    fun init(camera: CameraV2?, isPreviewStarted: Boolean) {
        setEGLContextClientVersion(2)

        val mCameraV2Renderer = CameraV2Renderer(this, camera, isPreviewStarted)
        setRenderer(mCameraV2Renderer)
    }
}