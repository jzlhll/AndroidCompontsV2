package com.au.audiorecordplayer

import com.au.audiorecordplayer.cam2.Camera2Fragment
import com.au.audiorecordplayer.cam2.impl.DataRepository
import com.au.audiorecordplayer.cam2.view.cam.PreviewMode
import com.au.module_android.utils.ViewBackgroundBuilder
import com.au.module_android.utils.unsafeLazy
import androidx.core.graphics.toColorInt
import com.au.module_android.click.onClick

class Camera2FragmentSettings(private val f: Camera2Fragment) {
    private val normalPreviewBg by unsafeLazy {
        ViewBackgroundBuilder()
            .setBackground("#8fffffff".toColorInt())
            .setCornerRadius(8f)
            .build()
    }

    private val currentPreviewBg by unsafeLazy {
        ViewBackgroundBuilder()
            .setBackground("#8f082c8f".toColorInt())
            .setCornerRadius(8f)
            .build()
    }


    fun initUis() {
        initPreviewModes()
        changePreviewModesBackground()
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