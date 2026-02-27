package com.au.module_imagecompressed.compressor

import android.graphics.Bitmap
import androidx.core.graphics.scale

/**
 * 将 Bitmap 按「短边占满、长边居中裁剪」的规则，裁剪为指定目标尺寸
 * @param targetWidth 目标宽度（必须 > 0）
 * @param targetHeight 目标高度（必须 > 0）
 * @return 裁剪后的精准目标尺寸 Bitmap；若参数不合法返回 null
 */
fun Bitmap.centerCropBitmapToTargetSize(
    targetWidth: Int,
    targetHeight: Int
): Bitmap? {
    // 1. 参数合法性校验
    if (this.isRecycled) {
        throw IllegalArgumentException("Error: Bitmap is recycled")
    }
    if (targetWidth <= 0 || targetHeight <= 0) {
        throw IllegalArgumentException("targetWidth and targetHeight must be greater than 0")
    }

    // 2. 获取源 Bitmap 尺寸
    val sourceW = this.width
    val sourceH = this.height

    // 3. 计算缩放比例：取「按宽度/高度缩放到目标尺寸」的较大值，保证短边占满
    val scaleForWidth = targetWidth.toFloat() / sourceW // 按宽度缩放的比例
    val scaleForHeight = targetHeight.toFloat() / sourceH // 按高度缩放的比例
    val fitScale = maxOf(scaleForWidth, scaleForHeight) // 取大比例 → 短边占满，长边超出

    // 4. 缩放 Bitmap 到「短边占满」的中间尺寸
    val fitW = (sourceW * fitScale).toInt()
    val fitH = (sourceH * fitScale).toInt()
    val fitBitmap = this.scale(fitW, fitH)

    // 5. 计算居中裁剪的坐标（仅裁剪超出目标尺寸的长边）
    val cropX = if (fitW > targetWidth) (fitW - targetWidth) / 2 else 0 // 宽度超出则居中
    val cropY = if (fitH > targetHeight) (fitH - targetHeight) / 2 else 0 // 高度超出则居中

    // 6. 执行居中裁剪，得到精准目标尺寸
    val croppedBitmap = try {
        Bitmap.createBitmap(
            fitBitmap,
            cropX,
            cropY,
            targetWidth,
            targetHeight
        )
    } catch (e: IllegalArgumentException) {
        // 极端情况（如尺寸计算异常）返回 null
        e.printStackTrace()
        null
    }

    if (fitBitmap != croppedBitmap && !fitBitmap.isRecycled) {
        fitBitmap.recycle()
    }

    return croppedBitmap
}