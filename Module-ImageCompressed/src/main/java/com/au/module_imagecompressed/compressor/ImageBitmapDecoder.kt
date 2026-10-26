package com.au.module_imagecompressed.compressor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import androidx.exifinterface.media.ExifInterface
import com.au.module_imagecompressed.compressor.imagesource.IImageSource

/**
 * 按目标长边解码图片，区分 RAW 与普通图片的解码路径及方向处理。
 */
internal class ImageBitmapDecoder(
    private val source: IImageSource,
    private val scaleMaxSide: Int?,
) {
    companion object {
        private val RAW_FILE_TYPES = setOf(
            "raw", "dng", "arw", "cr2", "cr3", "nef", "nrw", "orf", "pef", "raf", "rw2", "srw"
        )
    }

    data class Decoded(
        var originalW:Int, var originalH:Int,
        var targetW:Int, var targetH:Int, var sampleSize:Int, var bitmap: Bitmap?,
        var orientation: Int = ExifInterface.ORIENTATION_NORMAL) {
        override fun toString(): String {
            return "orig:${originalW}x$originalH, sample:$sampleSize, wish size:${targetW}x$targetH, decoded bitmap:${bitmap?.width}x${bitmap?.height}"
        }
    }

    /** 按目标尺寸解码图片，返回匹配输出像素的方向信息。 */
    fun decodeBitmap(sourceExif: ExifInterface): Decoded {
        val sourceOrientation = sourceExif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        if (source.extension.lowercase() in RAW_FILE_TYPES) {
            // BitmapFactory 不应用 EXIF 方向，RAW 转码后保留原方向标签，避免预览重复旋转。
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            source.openInputStream()?.use { BitmapFactory.decodeStream(it, null, options) }
            if (options.outWidth > 0 && options.outHeight > 0) {
                val originalWidth = options.outWidth
                val originalHeight = options.outHeight
                val (targetWidth, targetHeight) = if (scaleMaxSide != null) {
                    calculateTargetDimensions(
                        originalWidth,
                        originalHeight,
                        scaleMaxSide.toFloat(),
                        scaleMaxSide.toFloat(),
                    )
                } else {
                    originalWidth to originalHeight
                }
                val sampleSize = if (scaleMaxSide != null) {
                    // 向下取整，保证采样结果不小于目标尺寸，交由尺寸流水线完成最终缩放。
                    val widthSample = originalWidth / targetWidth
                    val heightSample = originalHeight / targetHeight
                    if (widthSample < heightSample) widthSample else heightSample
                } else {
                    1
                }
                options.inJustDecodeBounds = false
                options.inSampleSize = sampleSize
                options.inPreferredConfig = Bitmap.Config.ARGB_8888
                val bitmap = source.openInputStream()?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }
                if (bitmap != null) {
                    return Decoded(
                        originalWidth, originalHeight,
                        targetWidth, targetHeight, sampleSize, bitmap, sourceOrientation
                    )
                }
            }
        }
        val decoded = Decoded(0, 0, 0, 0, 0, null)
        // BitmapFactory 不支持的输入继续尝试 ImageDecoder，包括 Provider 提供的可解码表示。
        val decodeSource = source.createImageDecodeSource()
        decoded.bitmap = ImageDecoder.decodeBitmap(decodeSource) { decoder, info, _ ->
            // 通过ImageInfo获取原始尺寸
            val originalWidth = info.size.width
            val originalHeight = info.size.height
            decoded.originalW = originalWidth
            decoded.originalH = originalHeight

            // DNG 解码不应用源方向；普通格式和内嵌 JPEG 由 ImageDecoder 处理自身方向。
            if (info.mimeType == "image/x-adobe-dng") {
                decoded.orientation = sourceOrientation
            }
            // 关闭尺寸处理时不设置采样，保留系统解码尺寸。
            if (scaleMaxSide == null) {
                decoded.targetW = originalWidth
                decoded.targetH = originalHeight
                decoded.sampleSize = 1
            } else {
                val (targetWidth, targetHeight) = calculateTargetDimensions(
                    originalWidth,
                    originalHeight,
                    scaleMaxSide.toFloat(),
                    scaleMaxSide.toFloat(),
                )
                decoded.targetW = targetWidth
                decoded.targetH = targetHeight
                // 直接指定目标尺寸，由系统完成采样与缩放，避免整数采样过头。
                decoded.sampleSize = 1
                decoder.setTargetSize(targetWidth, targetHeight)
            }
        }
        return decoded
    }
}
