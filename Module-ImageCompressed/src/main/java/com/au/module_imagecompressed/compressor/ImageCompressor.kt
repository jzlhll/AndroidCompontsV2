package com.au.module_imagecompressed.compressor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Build
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.exifinterface.media.ExifInterface
import com.au.module_android.utils.ignoreError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.roundToInt

/**
 * 图片压缩类
 */
internal class ImageCompressor(
    val config : Config = Config(),
    val provideInputStream: () -> InputStream?,
    val provideFileSize: () -> Long,
    val provideImageDecodeSource:() ->ImageDecoder.Source?,
) {
    data class Config(
        val outputFormat: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        val maxWidth: Int = 1440,
        val maxHeight: Int = 1920,       // 最大边长限制，防止内存溢出
        var targetSize:Long? = null      /*压缩的目标大小 如果压缩到minQuality还是不行的话，就放弃。*/
    )

    private val tempBufferSize = 16 * 1024
    private val api: IApi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        NewApi()
    } else {
        OldApi()
    }

    private data class Decoded(
        var originalW:Int, var originalH:Int,
        var targetW:Int, var targetH:Int, var sampleSize:Int, var bitmap: Bitmap?) {
        override fun toString(): String {
            return "orig:${originalW}x$originalH, sampleSize:$sampleSize, target:${targetW}x$targetH, bitmap:${bitmap?.width}x${bitmap?.height}"
        }
    }

    private interface IApi {
        fun decodeBitmap() : Decoded
    }

    private inner class OldApi : IApi {
        override fun decodeBitmap() : Decoded {
            // 1. 解码图片边界（仅获取宽高，不加载完整Bitmap）
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            provideInputStream()?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            // 2. 计算目标缩放尺寸
            val (targetWidth, targetHeight) = calculateTargetDimensions(
                options.outWidth,
                options.outHeight
            )

            // 3. 计算采样率
            val sampleSize = calculateSampleSize(
                options.outWidth,
                options.outHeight,
                targetWidth,
                targetHeight
            )

            // 4. 解码并处理Bitmap（缩放+旋转）
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inJustDecodeBounds = false
                inTempStorage = ByteArray(tempBufferSize)
            }
            val bitmap = provideInputStream()?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }

            return Decoded(options.outWidth, options.outHeight, targetWidth, targetHeight, sampleSize, bitmap)
        }
    }

    private inner class NewApi : IApi {
        override fun decodeBitmap(): Decoded {
            val decoded = Decoded(0, 0, 0, 0, 0, null)
            //1. 解码图片
            decoded.bitmap = ImageDecoder.decodeBitmap(provideImageDecodeSource()!!) { decoder, info, _ ->
                // 通过ImageInfo获取原始尺寸
                val originalWidth = info.size.width
                val originalHeight = info.size.height
                decoded.originalW = originalWidth
                decoded.originalH = originalHeight

                // 2. 计算目标缩放尺寸
                val (targetWidth, targetHeight) = calculateTargetDimensions(
                    originalWidth,
                    originalHeight
                )
                decoded.targetW = targetWidth
                decoded.targetH = targetHeight

                // 3. 计算采样率
                val sampleSize = calculateSampleSize(
                    originalWidth,
                    originalHeight,
                    targetWidth,
                    targetHeight
                )
                decoded.sampleSize = sampleSize
                // 计算采样尺寸
                decoder.setTargetSampleSize(sampleSize)
            }
            return decoded
        }
    }

    suspend fun compress() : File?{
        return withContext(Dispatchers.IO) {
            val outputFile = CompressCacheConstManager.createCompressOutputFile()

            ignoreError {
                val decoded = api.decodeBitmap()
                Log.d("ImageProcessor", "compress: $decoded")
                var bitmap = decoded.bitmap ?: return@withContext null
                val targetW = decoded.targetW
                val targetH = decoded.targetH

                var isDown = false //允许尝试一次额外操作。

                // 查询文件大小 放到外面避免多次查询
                val fileSize = provideFileSize()
                var quality = chooseQuality(fileSize)
                Log.d("ImageProcessor", "compress: quality $quality")
                val targetSize = config.targetSize

                while (true) {
                    val scaledBitmap = if (isDown) {
                            quality -= 5
                            bitmap.scaleTo((targetW * 0.8f).toInt(), (targetH * 0.8f).toInt())
                        } else {
                            bitmap.scaleTo(targetW, targetH)
                        }
                    if (scaledBitmap != bitmap) {
                        bitmap.recycle()
                    }
                    bitmap = scaledBitmap
                    val targetBitmap = bitmap.rotateByExif { provideInputStream() }
                    if (bitmap != targetBitmap) {
                        bitmap.recycle()
                    }
                    bitmap = targetBitmap

                    // 6. 写入输出文件
                    var count = 3
                    while (count-- > 0) {
                        if (outputFile.exists()) {
                            outputFile.delete()
                            delay(100)
                        }
                        FileOutputStream(outputFile).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                        }
                        if (!isDown) {
                            break
                        }
                        if (targetSize != null && outputFile.length() > targetSize) {
                            continue
                        }
                    }

                    //7. 如果目标文件大小超过限制，就继续尝试压缩
                    if (targetSize != null && outputFile.length() > targetSize) {
                        Log.d("ImageProcessor", "⚠️ Target file size exceeds limit...")
                        if (!isDown) { //再给一次机会压缩
                            isDown = true
                            continue
                        } else {
                            Log.d("ImageProcessor", "❌ compress twice image still not small enough?")
                        }
                    }

                    bitmap.recycle()
                    break
                }
            }

            outputFile
        }
    }

    /**
     * Bitmap 缩放扩展函数
     */
    private fun Bitmap.scaleTo(width: Int, height: Int) : Bitmap {
        if (width <= 0 || height <= 0) return this
        if (width == this.width && height == this.height) return this
        Log.d("ImageProcessor", "Scale to $width * $height")

        // 修复点1：将硬件位图转换为软件位图
        val softwareBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && this.getConfig() == Bitmap.Config.HARDWARE) {
            // 硬件位图转换为软件位图（选择ARGB_8888保证兼容性，也可根据需求用RGB_565）
            this.copy(Bitmap.Config.ARGB_8888, true)
        } else {
            // 非硬件位图直接使用
            this
        }

        val scaled = createBitmap(width, height, Bitmap.Config.RGB_565)
        val ratioX = width / this.width.toFloat()
        val ratioY = height / this.height.toFloat()
        val midX = width / 2f
        val midY = height / 2f

        val matrix = Matrix().apply { setScale(ratioX, ratioY, midX, midY) }
        Canvas(scaled).apply {
            setMatrix(matrix)
            drawBitmap(
                softwareBitmap,
                midX - this@scaleTo.width / 2f,
                midY - this@scaleTo.height / 2f,
                Paint(Paint.FILTER_BITMAP_FLAG)
            )
        }
        // 修复点3：如果创建了新的软件位图，回收临时对象（避免内存泄漏）
        if (softwareBitmap !== this) {
            softwareBitmap.recycle()
        }
        return scaled
    }

    /**
     * 根据Exif信息旋转Bitmap（适配File输入）
     */
    private fun Bitmap.rotateByExif(inputStreamProvider: () -> InputStream?): Bitmap {
        ignoreError {
            inputStreamProvider()?.use {
                val exif = ExifInterface(it)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED
                )

                val rotateMatrix = Matrix().apply {
                    when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                        ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                        ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                        else -> {}
                    }
                }
                return Bitmap.createBitmap(this, 0, 0, width, height, rotateMatrix, true)
            }
        }
        return this
    }

    /**
     * 根据文件大小选择压缩质量
     */
    private fun chooseQuality(fileSize: Long): Int {
        return when {
            fileSize > 16_000_000 -> 60    // >5MB → 40%质量
            fileSize > 8_000_000 -> 70    // >2MB → 50%质量
            fileSize > 4_000_000 -> 80    // >1MB → 60%质量
            fileSize > 2_000_000 -> 85    // >500KB → 70%质量
            fileSize > 1_000_000 -> 90      // >250KB → 80%质量
            else -> 100                   // ≤250KB → 90%质量
        }
    }

    private fun calculateTargetDimensions(
        width: Int,
        height: Int,
    ): Pair<Int, Int> {
        var w = width.toFloat()
        var h = height.toFloat()

        val maxWidth = config.maxWidth.toFloat()
        val maxHeight = config.maxHeight.toFloat()

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

    private fun calculateSampleSize(
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
}