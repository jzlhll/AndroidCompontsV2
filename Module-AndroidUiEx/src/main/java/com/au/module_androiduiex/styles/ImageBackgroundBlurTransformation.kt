package com.au.module_androiduiex.styles

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import com.bumptech.glide.load.Key
import com.bumptech.glide.load.engine.bitmap_recycle.ArrayPool
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.nio.ByteBuffer
import java.security.MessageDigest

/** 图片模糊背景请求的降采样倍率。 */
const val IMAGE_BACKGROUND_BLUR_DOWNSAMPLE = 4
private const val IMAGE_BACKGROUND_BLUR_PASSES = 3

/**
 * 按目标尺寸居中裁剪并生成低分辨率模糊背景。
 * [blurRadiusPx] 使用显示尺寸下的像素半径，请求尺寸按 [IMAGE_BACKGROUND_BLUR_DOWNSAMPLE] 缩小。
 */
class ImageBackgroundBlurTransformation(
    private val blurRadiusPx: Int,
    private val arrayPool: ArrayPool,
) : BitmapTransformation() {

    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int,
    ): Bitmap {
        val requestedWidth = if (outWidth > 0) outWidth else toTransform.width
        val requestedHeight = if (outHeight > 0) outHeight else toTransform.height
        val targetWidth = requestedWidth
        val targetHeight = requestedHeight
        val result = pool.get(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        result.setHasAlpha(true)
        result.density = toTransform.density
        result.eraseColor(Color.TRANSPARENT)

        val widthScale = targetWidth.toFloat() / toTransform.width
        val heightScale = targetHeight.toFloat() / toTransform.height
        val scale = if (widthScale > heightScale) widthScale else heightScale
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(
                (targetWidth - toTransform.width * scale) / 2f,
                (targetHeight - toTransform.height * scale) / 2f,
            )
        }
        Canvas(result).drawBitmap(
            toTransform,
            matrix,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )

        val sampledRadius = blurRadiusPx / IMAGE_BACKGROUND_BLUR_DOWNSAMPLE
        val shortestSide = if (targetWidth < targetHeight) targetWidth else targetHeight
        val maximumRadius = shortestSide - 1
        val resolvedRadius = when {
            sampledRadius < 1 -> 1
            sampledRadius > maximumRadius -> maximumRadius
            else -> sampledRadius
        }
        if (resolvedRadius > 0) {
            result.applyBoxBlur(resolvedRadius, arrayPool)
        }
        return result
    }

    override fun equals(other: Any?): Boolean {
        return other is ImageBackgroundBlurTransformation && blurRadiusPx == other.blurRadiusPx
    }

    override fun hashCode(): Int = 31 * ID.hashCode() + blurRadiusPx

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update(ID_BYTES)
        messageDigest.update(ByteBuffer.allocate(4).putInt(blurRadiusPx).array())
    }

    private companion object {
        const val ID = "com.au.module_androiduiex.styles.ImageBackgroundBlurTransformation.v1"
        val ID_BYTES: ByteArray = ID.toByteArray(Key.CHARSET)
    }
}

private fun Bitmap.applyBoxBlur(radius: Int, arrayPool: ArrayPool) {
    val pixelCount = width * height
    val source = arrayPool.get(pixelCount, IntArray::class.java)
    val horizontal = arrayPool.get(pixelCount, IntArray::class.java)
    try {
        getPixels(source, 0, width, 0, 0, width, height)
        var pass = 0
        while (pass < IMAGE_BACKGROUND_BLUR_PASSES) {
            blurHorizontally(source, horizontal, width, height, radius)
            blurVertically(horizontal, source, width, height, radius)
            pass++
        }
        setPixels(source, 0, width, 0, 0, width, height)
    } finally {
        arrayPool.put(source)
        arrayPool.put(horizontal)
    }
}

private fun blurHorizontally(
    source: IntArray,
    destination: IntArray,
    width: Int,
    height: Int,
    radius: Int,
) {
    val kernelSize = radius * 2 + 1
    val lastX = width - 1
    var y = 0
    while (y < height) {
        val rowStart = y * width
        var alpha = 0
        var red = 0
        var green = 0
        var blue = 0
        var kernelX = -radius
        while (kernelX <= radius) {
            val color = source[rowStart + boundedIndex(kernelX, lastX)]
            alpha += color ushr 24
            red += color shr 16 and 0xFF
            green += color shr 8 and 0xFF
            blue += color and 0xFF
            kernelX++
        }
        var x = 0
        while (x < width) {
            destination[rowStart + x] = composeColor(alpha, red, green, blue, kernelSize)
            val removedColor = source[rowStart + boundedIndex(x - radius, lastX)]
            val addedColor = source[rowStart + boundedIndex(x + radius + 1, lastX)]
            alpha += (addedColor ushr 24) - (removedColor ushr 24)
            red += (addedColor shr 16 and 0xFF) - (removedColor shr 16 and 0xFF)
            green += (addedColor shr 8 and 0xFF) - (removedColor shr 8 and 0xFF)
            blue += (addedColor and 0xFF) - (removedColor and 0xFF)
            x++
        }
        y++
    }
}

private fun blurVertically(
    source: IntArray,
    destination: IntArray,
    width: Int,
    height: Int,
    radius: Int,
) {
    val kernelSize = radius * 2 + 1
    val lastY = height - 1
    var x = 0
    while (x < width) {
        var alpha = 0
        var red = 0
        var green = 0
        var blue = 0
        var kernelY = -radius
        while (kernelY <= radius) {
            val color = source[boundedIndex(kernelY, lastY) * width + x]
            alpha += color ushr 24
            red += color shr 16 and 0xFF
            green += color shr 8 and 0xFF
            blue += color and 0xFF
            kernelY++
        }
        var y = 0
        while (y < height) {
            destination[y * width + x] = composeColor(alpha, red, green, blue, kernelSize)
            val removedColor = source[boundedIndex(y - radius, lastY) * width + x]
            val addedColor = source[boundedIndex(y + radius + 1, lastY) * width + x]
            alpha += (addedColor ushr 24) - (removedColor ushr 24)
            red += (addedColor shr 16 and 0xFF) - (removedColor shr 16 and 0xFF)
            green += (addedColor shr 8 and 0xFF) - (removedColor shr 8 and 0xFF)
            blue += (addedColor and 0xFF) - (removedColor and 0xFF)
            y++
        }
        x++
    }
}

private fun boundedIndex(value: Int, lastIndex: Int): Int {
    return when {
        value < 0 -> 0
        value > lastIndex -> lastIndex
        else -> value
    }
}

private fun composeColor(
    alpha: Int,
    red: Int,
    green: Int,
    blue: Int,
    divisor: Int,
): Int {
    return (alpha / divisor shl 24) or
        (red / divisor shl 16) or
        (green / divisor shl 8) or
        (blue / divisor)
}
