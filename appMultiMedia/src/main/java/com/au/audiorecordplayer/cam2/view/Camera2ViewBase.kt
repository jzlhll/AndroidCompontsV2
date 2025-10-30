package com.au.audiorecordplayer.cam2.view

interface Camera2ViewBase {
    /**
     * 创建相机函数
     */
    var openCameraFunc:()->Unit
    /**
     * 关闭相机函数
     */
    var closeCameraFunc:()->Unit
}