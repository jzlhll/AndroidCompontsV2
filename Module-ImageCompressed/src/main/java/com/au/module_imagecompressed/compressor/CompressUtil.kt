package com.au.module_imagecompressed.compressor

import android.content.Context
import android.net.Uri
import java.io.File
import kotlin.math.roundToInt

/**
 * 从本地文件读取图片像素宽高。常规格式优先走 BitmapFactory 读取 bounds，失败时回退 ImageDecoder。
 *
 * @param file 图片文件
 * @return 宽与高；失败或尺寸无效时返回 null
 */
fun decodeImagePixelSizeByImageDecoder(file: File): Pair<Int, Int>? {
    return SizeParseUtil().decodeImagePixelSize(file)
}

/**
 * 从 Uri 读取图片像素宽高。常规格式优先走 BitmapFactory 读取 bounds，失败时回退 ImageDecoder。
 *
 * @param context 上下文
 * @param uri 图片 Uri
 * @return 宽与高；失败或尺寸无效时返回 null
 */
fun decodeImagePixelSizeByImageDecoder(context: Context, uri: Uri): Pair<Int, Int>? {
    return SizeParseUtil().decodeImagePixelSize(context, uri)
}

internal fun calculateSampleSize(
    origW: Int,
    origH: Int,
    reqWidth: Int,
    reqHeight: Int
): Int {
    var inSample = 1
    if (origH > reqHeight || origW > reqWidth) {
        val heightRatio = (origH.toFloat() / reqHeight).roundToInt()
        val widthRatio = (origW.toFloat() / reqWidth).roundToInt()
        inSample = minOf(heightRatio, widthRatio)
    }

    val totalPixels = origW * origH.toFloat()
    val cap = reqWidth * reqHeight * 2f
    while (totalPixels / (inSample * inSample) > cap) {
        inSample++
    }
    return inSample
}

internal fun calculateTargetDimensions(
    width: Int,
    height: Int,
    maxWidth: Float,
    maxHeight: Float
): Pair<Int, Int> {
    var w = width.toFloat()
    var h = height.toFloat()

    val imgRatio = w / h
    val maxRatio = maxWidth / maxHeight

    if (h > maxHeight || w > maxWidth) {
        if (imgRatio < maxRatio) {
            val scale = maxHeight / h
            w *= scale
            h = maxHeight
        } else if (imgRatio > maxRatio) {
            val scale = maxWidth / w
            h *= scale
            w = maxWidth
        } else {
            w = maxWidth
            h = maxHeight
        }
    }
    return w.roundToInt() to h.roundToInt()
}