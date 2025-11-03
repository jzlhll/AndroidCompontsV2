package com.au.audiorecordplayer.cam2

import android.graphics.drawable.Drawable
import androidx.core.graphics.toColorInt
import com.au.audiorecordplayer.cam2.impl.DataRepository
import com.au.audiorecordplayer.cam2.view.cam.PreviewMode
import com.au.audiorecordplayer.cam2.view.gl.FilterType
import com.au.module_android.click.onClick
import com.au.module_android.utils.ViewBackgroundBuilder
import com.au.module_android.utils.logdNoFile
import com.au.module_android.utils.unsafeLazy

class Camera2FragmentSettings(private val f: Camera2Fragment) {
    private val currentBg = "#cf3dc4dc"

    private val normalPreviewBg : Drawable
        get() {
        return ViewBackgroundBuilder()
            .setBackground("#cfffffff".toColorInt())
            .setCornerRadius(8f)
            .build()!!
    }

    private val currentPreviewBg : Drawable
        get() {
        return ViewBackgroundBuilder()
            .setBackground(currentBg.toColorInt())
            .setCornerRadius(8f)
            .build()!!
    }

    fun initUis() {
        f.binding.expandSettings.setOnExpansionUpdateListener { d, expansion ->
            changeToNullBgs()
            logdNoFile{"d: $d, expansion $expansion"}
            if (expansion == 3 || expansion == 0) {
                f.binding.expandSettings.post {
                    if (f.binding.expandSettings.isAttachedToWindow) {
                        changeShadersBackground()
                        changePreviewModesBackground()
                    }
                }
            }
        }

        initPreviewModes()
        initShaders()
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

    private fun changeToNullBgs() {
        f.binding.shaderOriginal.background = null
        f.binding.shaderInvert.background = null
        f.binding.shaderGray.background = null
        f.binding.shaderSepia.background = null
        f.binding.previewViewSurface.background = null
        f.binding.previewViewTexture.background = null
        f.binding.previewViewGL.background = null
    }

    private fun changeShadersBackground() {
        val nbg = normalPreviewBg
        val cbg = currentPreviewBg
        when(DataRepository.shaderModeFlow.value) {
            FilterType.ORIGINAL -> {
                f.binding.shaderOriginal.background = cbg
                f.binding.shaderInvert.background = nbg
                f.binding.shaderGray.background = nbg
                f.binding.shaderSepia.background = nbg
            }
            FilterType.SEPIA -> {
                f.binding.shaderOriginal.background = nbg
                f.binding.shaderInvert.background = nbg
                f.binding.shaderGray.background = nbg
                f.binding.shaderSepia.background = cbg
            }
            FilterType.GRAY -> {
                f.binding.shaderOriginal.background = nbg
                f.binding.shaderInvert.background = nbg
                f.binding.shaderGray.background = cbg
                f.binding.shaderSepia.background = nbg
            }
            FilterType.INVERT -> {
                f.binding.shaderOriginal.background = nbg
                f.binding.shaderInvert.background = cbg
                f.binding.shaderGray.background = nbg
                f.binding.shaderSepia.background = nbg
            }
            else -> {}
        }
    }

    fun toggle() {
        f.binding.expandSettings.toggle()
    }

    private fun initPreviewModes() {
        f.binding.previewViewSurface.onClick {
            if (DataRepository.previewMode != PreviewMode.SURFACE_VIEW) DataRepository.previewMode = PreviewMode.SURFACE_VIEW
            changePreviewModesBackground()
            f.toastOnText("切换预览为SurfaceView，下次启动生效")
            toggle()
        }
        f.binding.previewViewTexture.onClick {
            if (DataRepository.previewMode != PreviewMode.TEXTURE_VIEW) DataRepository.previewMode = PreviewMode.TEXTURE_VIEW
            changePreviewModesBackground()
            f.toastOnText("切换预览为TextureView，下次启动生效")
            toggle()
        }
        f.binding.previewViewGL.onClick {
            if (DataRepository.previewMode != PreviewMode.GL_SURFACE_VIEW) DataRepository.previewMode = PreviewMode.GL_SURFACE_VIEW
            changePreviewModesBackground()
            f.toastOnText("切换预览为GLSurfaceView，下次启动生效")
            toggle()
        }
        changePreviewModesBackground()
    }

    fun changePreviewModesBackground() {
        val cbg = currentPreviewBg
        val nbg = normalPreviewBg
        when(DataRepository.previewMode) {
            PreviewMode.SURFACE_VIEW -> {
                f.binding.previewViewSurface.background = cbg
                f.binding.previewViewTexture.background = nbg
                f.binding.previewViewGL.background = nbg
            }
            PreviewMode.TEXTURE_VIEW -> {
                f.binding.previewViewSurface.background = nbg
                f.binding.previewViewTexture.background = cbg
                f.binding.previewViewGL.background = nbg
            }
            PreviewMode.GL_SURFACE_VIEW -> {
                f.binding.previewViewSurface.background = nbg
                f.binding.previewViewTexture.background = nbg
                f.binding.previewViewGL.background = cbg
            }
        }
    }
}