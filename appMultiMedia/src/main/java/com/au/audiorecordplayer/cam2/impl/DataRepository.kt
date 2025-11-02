package com.au.audiorecordplayer.cam2.impl

import android.hardware.camera2.CameraCharacteristics
import android.view.Surface
import com.au.audiorecordplayer.cam2.view.cam.PreviewMode
import com.au.audiorecordplayer.cam2.view.gl.FilterType
import com.au.audiorecordplayer.cam2.view.gl.toName
import com.au.audiorecordplayer.cam2.view.gl.toType
import com.au.module_cached.delegate.AppDataStoreIntCache
import com.au.module_cached.delegate.AppDataStoreStringCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object DataRepository {
    const val KEY_CAMERA_ID_SAVED = "camera2Demo_cameraId"
    const val KEY_CAMERA_PREVIEW_MODE = "camera2Demo_previewMode"
    const val KEY_SHADER_MODE = "camera2Demo_shaderMode"

    /**
     * 放在全局
     */
    var surface : Surface? = null

    var cameraId by AppDataStoreIntCache(KEY_CAMERA_ID_SAVED, CameraCharacteristics.LENS_FACING_BACK)

    private val defaultPreviewMode = PreviewMode.SURFACE_VIEW

    private var previewModeStr by AppDataStoreStringCache(KEY_CAMERA_PREVIEW_MODE, defaultPreviewMode.value)
    private var _previewMode: PreviewMode? = null

    var previewMode : PreviewMode
        get() {
            if (_previewMode == null) {
                _previewMode = PreviewMode.entries.firstOrNull { it.value == previewModeStr } ?: defaultPreviewMode
            }
            return _previewMode!!
        }
        set(value) {
            _previewMode = value
            previewModeStr = value.value
        }

    private var shaderModeStr by AppDataStoreStringCache(KEY_SHADER_MODE, FilterType.ORIGINAL.toName())
    private val _shaderModeFlow = MutableStateFlow(shaderModeStr.toType())
    val shaderModeFlow = _shaderModeFlow.asStateFlow()

    fun updateShaderMode(value: FilterType) {
        shaderModeStr = value.toName()
        _shaderModeFlow.value = value
    }
}