package com.au.audiorecordplayer.cam2.view

import android.view.Surface

interface ICamFunction {
    /**
     * 创建相机函数
     */
    var openCameraFunc:(surface:Surface)->Unit
    /**
     * 关闭相机函数
     */
    var closeCameraFunc:()->Unit
}