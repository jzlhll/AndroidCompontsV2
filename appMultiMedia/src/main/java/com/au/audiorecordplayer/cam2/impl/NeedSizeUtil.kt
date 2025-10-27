package com.au.audiorecordplayer.cam2.impl

import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Size
import android.view.SurfaceHolder
import com.au.audiorecordplayer.cam2.view.Camera2View
import com.au.audiorecordplayer.util.MyLog
import java.lang.Long
import kotlin.Array
import kotlin.Comparator
import kotlin.Int
import kotlin.RuntimeException
import kotlin.String
import kotlin.math.abs

abstract class NeedSizeUtil(cameraManager: CameraManager,
                            cameraIdStr: String,
                            val wishWidth: Int,
                            val wishHeight: Int) {
    val map = cameraManager.getCameraCharacteristics(cameraIdStr).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

    companion object {
        fun getByFmt(fmt:Int,
                     cameraManager: CameraManager,
                     cameraIdStr: String,
                     wishWidth: Int, wishHeight: Int) : NeedSizeUtil {
            return FmtNeedSizeUtil(fmt, cameraManager, cameraIdStr, wishWidth, wishHeight)
        }

        fun getByClz(clz: Class<*>,
                     cameraManager: CameraManager,
                     cameraIdStr: String,
                     wishWidth: Int, wishHeight: Int) : NeedSizeUtil {
            return ClzNeedSizeUtil(clz, cameraManager, cameraIdStr, wishWidth, wishHeight)
        }

        fun needSizeFmtClass(previewMode: Camera2View.PreviewMode) : Class<*> {
            return when(previewMode) {
                Camera2View.PreviewMode.SURFACE_VIEW -> SurfaceHolder::class.java
                Camera2View.PreviewMode.TEXTURE_VIEW -> SurfaceTexture::class.java
                Camera2View.PreviewMode.GL_SURFACE_VIEW -> SurfaceTexture::class.java
            }
        }
    }

    class CompareSizesByArea : Comparator<Size> {
        override fun compare(lhs: Size, rhs: Size): Int {
            return Long.signum(
                lhs.getWidth().toLong() * lhs.getHeight() -
                        rhs.getWidth().toLong() * rhs.getHeight()
            )
        }
    }

    protected fun needSizeBySizes(
        outputSizes: Array<Size>?,
        wishWidth: Int,
        wishHeight: Int,
        from: String
    ): Size {
        var needSize: Size? = null
        var needRadioW = Double.MAX_VALUE / 3
        var needRadioH = needRadioW
        if (outputSizes != null && outputSizes.isNotEmpty()) {
            needSize = outputSizes[0]
            var i = 0
            while (i < outputSizes.size) {
                val size = outputSizes[i]
                val radioW = abs(1.0 - size.width.toFloat() / wishWidth)
                val radioH = abs(1.0 - size.height.toFloat() / wishHeight)
                val plus = radioW + radioH
                if (plus < needRadioW + needRadioH) {
                    needRadioW = radioW
                    needRadioH = radioH
                    needSize = size
                }
                i++
            }
        }

        if (needSize == null) {
            throw RuntimeException("No need Camera Size!")
        }
        MyLog.d("$from size2: " + needSize.width + " " + needSize.height)
        return needSize
    }

    abstract fun needSize(from:String): Size
}

private class FmtNeedSizeUtil(val fmt:Int,
                              cameraManager: CameraManager,
                              cameraIdStr: String,
                              wishWidth: Int, wishHeight: Int)
    : NeedSizeUtil(cameraManager, cameraIdStr, wishWidth, wishHeight) {
    override fun needSize(from:String): Size {
        val outputSizes: Array<Size>? = map?.getOutputSizes(fmt)
        return needSizeBySizes(outputSizes, wishWidth, wishHeight, from)
    }
}

private class ClzNeedSizeUtil(val clz:Class<*>,
                              cameraManager: CameraManager,
                              cameraIdStr: String,
                              wishWidth: Int, wishHeight: Int)
        : NeedSizeUtil(cameraManager, cameraIdStr, wishWidth, wishHeight) {
    override fun needSize(from: String): Size {
        val outputSizes: Array<Size>? = map?.getOutputSizes(clz)
        return needSizeBySizes(outputSizes, wishWidth, wishHeight, from)
    }
}