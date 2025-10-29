package com.au.audiorecordplayer.cam2.view

interface IViewOnSurfaceCallback<T> {
    fun onSurfaceCreated(surfaceTextureOr: T)
    fun onSurfaceDestroyed()
    fun onSurfaceChanged()
}