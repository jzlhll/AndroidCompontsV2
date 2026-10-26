package com.au.module_imagecompressed.compressor

import android.graphics.*
import androidx.core.graphics.createBitmap

/**
 * 尺寸流水线：按长边上限等比缩小 Bitmap，并为 JPEG 合成白底；输入及返回结果由调用方统一回收。
 */
internal class ImageScalePipeline(
    private val scaleMaxSide: Int?,
    private val outputFormat: Bitmap.CompressFormat,
) {
    /** 长边超过上限时等比缩小；JPEG 透明图合成白底，其余无需处理时复用输入 Bitmap。 */
    fun process(inputBitmap: Bitmap): Bitmap {
        val (targetW, targetH) = if (scaleMaxSide != null) {
            calculateTargetDimensions(
                inputBitmap.width,
                inputBitmap.height,
                scaleMaxSide.toFloat(),
                scaleMaxSide.toFloat(),
            )
        } else {
            inputBitmap.width to inputBitmap.height
        }
        val needScale = targetW < inputBitmap.width || targetH < inputBitmap.height
        val needWhiteBackground = outputFormat == Bitmap.CompressFormat.JPEG && inputBitmap.hasAlpha()
        return if (needScale || needWhiteBackground) {
            inputBitmap.scaleTo(targetW, targetH)
        } else {
            inputBitmap
        }
    }

    private fun Bitmap.scaleTo(wishWidth: Int, wishHeight: Int): Bitmap {
        // 硬件 Bitmap 先转换为可绘制的软件 Bitmap。
        val softwareBitmap = if (this.config == Bitmap.Config.HARDWARE) {
            copy(Bitmap.Config.ARGB_8888, true)
        } else {
            this
        }
        try {
            val scaled = createBitmap(wishWidth, wishHeight, Bitmap.Config.ARGB_8888)
            try {
                val ratioX = wishWidth / width.toFloat()
                val ratioY = wishHeight / height.toFloat()
                val midX = wishWidth / 2f
                val midY = wishHeight / 2f
                val matrix = Matrix().apply { setScale(ratioX, ratioY, midX, midY) }
                Canvas(scaled).apply {
                    // JPEG 丢弃透明度前先铺白底，透明及半透明像素均在白底上合成。
                    if (outputFormat == Bitmap.CompressFormat.JPEG) {
                        drawColor(Color.WHITE)
                    }
                    setMatrix(matrix)
                    drawBitmap(
                        softwareBitmap,
                        midX - this@scaleTo.width / 2f,
                        midY - this@scaleTo.height / 2f,
                        Paint(Paint.FILTER_BITMAP_FLAG)
                    )
                }
                scaled.setHasAlpha(outputFormat != Bitmap.CompressFormat.JPEG && softwareBitmap.hasAlpha())
                return scaled
            } catch (error: Throwable) {
                scaled.recycle()
                throw error
            }
        } finally {
            if (softwareBitmap !== this) {
                softwareBitmap.recycle()
            }
        }
    }
}
