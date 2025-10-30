package com.au.audiorecordplayer.cam2.view

interface IViewOnSurfaceCallback {
    fun onSurfaceCreated(surfaceHolderOrSurfaceTexture: Any)
    fun onSurfaceDestroyed()
    fun onSurfaceChanged()
}