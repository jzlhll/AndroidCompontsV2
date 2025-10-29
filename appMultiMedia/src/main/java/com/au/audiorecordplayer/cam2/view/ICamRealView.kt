package com.au.audiorecordplayer.cam2.view

interface ICamRealView<T> {
    fun setCallback(cb: IViewOnSurfaceCallback<T>)
}