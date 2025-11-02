package com.au.audiorecordplayer.cam2.view

import android.view.View

interface Camera2ViewBase {
    var realView: View?
    /**
     * 创建相机函数
     */
    var openCameraFunc:()->Unit
    /**
     * 关闭相机函数
     */
    var closeCameraFunc:()->Unit
}