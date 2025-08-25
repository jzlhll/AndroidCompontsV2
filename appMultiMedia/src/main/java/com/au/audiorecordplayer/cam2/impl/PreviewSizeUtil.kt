package com.au.audiorecordplayer.cam2.impl

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.StreamConfigurationMap
import android.util.Size
import com.au.audiorecordplayer.util.MyLog
import java.lang.Long
import kotlin.Any
import kotlin.Array
import kotlin.Comparator
import kotlin.Int
import kotlin.RuntimeException
import kotlin.String
import kotlin.math.abs

class PreviewSizeUtil {
    class CompareSizesByArea : Comparator<Size> {
        override fun compare(lhs: Size, rhs: Size): Int {
            return Long.signum(
                lhs.getWidth().toLong() * lhs.getHeight() -
                        rhs.getWidth().toLong() * rhs.getHeight()
            )
        }
    }

    fun needSize(from:String, fmt:Any, cameraManager: CameraManager, cameraIdStr: String, wishWidth: Int, wishHeight: Int): Size {
        val camCharacteristics = cameraManager.getCameraCharacteristics(cameraIdStr)
        var sizes: Array<Size>? = null
        var needSize: Size? = null
        val map: StreamConfigurationMap? = camCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        if (map != null) {
            if (fmt is Int) {
                sizes = map.getOutputSizes(fmt)
            } else if(fmt is Class<*>) {
                sizes = map.getOutputSizes(fmt)
            }
        }
        var needRadioW = Double.MAX_VALUE / 3
        var needRadioH = needRadioW
        if (sizes != null && sizes.isNotEmpty()) {
            needSize = sizes[0]
            var i = 0
            while (i < sizes.size) {
                val size = sizes[i]
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
}