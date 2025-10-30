package com.au.audiorecordplayer.cam2.view

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.SurfaceHolder

/**
 * 二选一
 */
data class SurfaceFixSizeUnion(
    val surfaceTexture: SurfaceTexture? = null,
    val surfaceHolder: SurfaceHolder? = null,

    val shownSurface: Surface
)