package com.au.audiorecordplayer.cam2.impl

import android.hardware.camera2.CameraCharacteristics
import android.view.Surface
import com.au.audiorecordplayer.cam2.view.cam.PreviewMode
import com.au.module_cached.delegate.AppDataStoreIntCache
import com.au.module_cached.delegate.AppDataStoreStringCache

object DataRepository {
    const val KEY_CAMERA_ID_SAVED = "camera2Demo_cameraId"
    const val KEY_CAMERA_PREVIEW_MODE = "camera2Demo_previewMode"

    /**
     * 放在全局
     */
    var surface : Surface? = null

    var cameraId by AppDataStoreIntCache(KEY_CAMERA_ID_SAVED, CameraCharacteristics.LENS_FACING_BACK)

    private val defaultPreviewMode = PreviewMode.SURFACE_VIEW

    var previewModeStr by AppDataStoreStringCache(KEY_CAMERA_PREVIEW_MODE, defaultPreviewMode.value)
    val previewMode : PreviewMode
        get() = PreviewMode.entries.firstOrNull { it.value == previewModeStr } ?: defaultPreviewMode
}