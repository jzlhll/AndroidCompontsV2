package com.au.audiorecordplayer.cam2

import androidx.core.graphics.toColorInt
import com.au.audiorecordplayer.cam2.impl.DataRepository
import com.au.audiorecordplayer.cam2.view.cam.PreviewMode
import com.au.audiorecordplayer.cam2.view.gl.FilterType
import com.au.module_android.click.onClick
import com.au.module_android.utils.ViewBackgroundBuilder
import com.au.module_android.utils.unsafeLazy

class Camera2FragmentSettings(private val f: Camera2Fragment) {
    private val currentBg = "#cf3dc4dc"

    private val normalPreviewBg by unsafeLazy {
        ViewBackgroundBuilder()
            .setBackground("#cfffffff".toColorInt())
            .setCornerRadius(8f)
            .build()
    }

    private val currentPreviewBg by unsafeLazy {
        ViewBackgroundBuilder()
            .setBackground(currentBg.toColorInt())
            .setCornerRadius(8f)
            .build()
    }

    fun initUis() {
        initPreviewModes()
        changePreviewModesBackground()

        initShaders()
        changeShadersBackground()
    }

    private fun initShaders() {
        f.binding.shaderOriginal.onClick {
            if (DataRepository.shaderModeFlow.value != FilterType.ORIGINAL) DataRepository.updateShaderMode(FilterType.ORIGINAL)
            changeShadersBackground()
        }
        f.binding.shaderSepia.onClick {
            if(DataRepository.shaderModeFlow.value != FilterType.SEPIA) DataRepository.updateShaderMode(FilterType.SEPIA)
            changeShadersBackground()
        }
        f.binding.shaderInvert.onClick {
            if (DataRepository.shaderModeFlow.value != FilterType.INVERT) DataRepository.updateShaderMode(FilterType.INVERT)
            changeShadersBackground()
        }
        f.binding.shaderGray.onClick {
            if (DataRepository.shaderModeFlow.value != FilterType.GRAY) DataRepository.updateShaderMode(FilterType.GRAY)
            changeShadersBackground()
        }
    }

    private fun changeShadersBackground() {
        when(DataRepository.shaderModeFlow.value) {
            FilterType.ORIGINAL -> {
                f.binding.shaderOriginal.background = currentPreviewBg
                f.binding.shaderInvert.background = normalPreviewBg
                f.binding.shaderGray.background = normalPreviewBg
                f.binding.shaderSepia.background = normalPreviewBg
            }
            FilterType.SEPIA -> {
                f.binding.shaderOriginal.background = normalPreviewBg
                f.binding.shaderInvert.background = normalPreviewBg
                f.binding.shaderGray.background = normalPreviewBg
                f.binding.shaderSepia.background = currentPreviewBg
            }
            FilterType.GRAY -> {
                f.binding.shaderOriginal.background = normalPreviewBg
                f.binding.shaderInvert.background = normalPreviewBg
                f.binding.shaderGray.background = currentPreviewBg
                f.binding.shaderSepia.background = normalPreviewBg
            }
            FilterType.INVERT -> {
                f.binding.shaderOriginal.background = normalPreviewBg
                f.binding.shaderInvert.background = currentPreviewBg
                f.binding.shaderGray.background = normalPreviewBg
                f.binding.shaderSepia.background = normalPreviewBg
            }
            else -> {}
        }
    }

    private fun initPreviewModes() {
        f.binding.previewViewSurface.onClick {
            if (DataRepository.previewMode != PreviewMode.SURFACE_VIEW) DataRepository.previewMode = PreviewMode.SURFACE_VIEW
            changePreviewModesBackground()
            f.binding.expandSettings.toggle()
            f.toastOnText("切换预览为SurfaceView，下次启动生效")
        }
        f.binding.previewViewTexture.onClick {
            if (DataRepository.previewMode != PreviewMode.TEXTURE_VIEW) DataRepository.previewMode = PreviewMode.TEXTURE_VIEW
            changePreviewModesBackground()
            f.binding.expandSettings.toggle()
            f.toastOnText("切换预览为TextureView，下次启动生效")
        }
        f.binding.previewViewGL.onClick {
            if (DataRepository.previewMode != PreviewMode.GL_SURFACE_VIEW) DataRepository.previewMode = PreviewMode.GL_SURFACE_VIEW
            changePreviewModesBackground()
            f.binding.expandSettings.toggle()
            f.toastOnText("切换预览为GLSurfaceView，下次启动生效")
        }
        changePreviewModesBackground()
    }

    fun changePreviewModesBackground() {
        when(DataRepository.previewMode) {
            PreviewMode.SURFACE_VIEW -> {
                f.binding.previewViewSurface.background = currentPreviewBg
                f.binding.previewViewTexture.background = normalPreviewBg
                f.binding.previewViewGL.background = normalPreviewBg
            }
            PreviewMode.TEXTURE_VIEW -> {
                f.binding.previewViewSurface.background = normalPreviewBg
                f.binding.previewViewTexture.background = currentPreviewBg
                f.binding.previewViewGL.background = normalPreviewBg
            }
            PreviewMode.GL_SURFACE_VIEW -> {
                f.binding.previewViewSurface.background = normalPreviewBg
                f.binding.previewViewTexture.background = normalPreviewBg
                f.binding.previewViewGL.background = currentPreviewBg
            }
        }
    }
}