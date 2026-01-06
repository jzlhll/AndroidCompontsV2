package com.au.module_imagecompressed.compressor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 图片压缩抽象基类
 * 抽离所有共用的属性和方法，子类只需实现各自的 compress 逻辑
 */
abstract class BaseImageCompressor {
    // 共用属性
    protected val maxHeight = 1920f
    protected val maxWidth = 1080f
    protected val tempBufferSize = 16 * 1024

    /**
     * 解码图片边界（仅获取宽高，不加载完整Bitmap）
     */
    private fun decodeBounds(context: Context, source: Any): BitmapFactory.Options {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        provideInputStream(context, source)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
        return options
    }

    protected fun decodeBefore(context : Context, source:Any) : Triple<Int, Int, Int> {
        // 1. 解码图片边界
        val originalOptions = decodeBounds(context, source)

        // 2. 计算目标缩放尺寸
        val (targetWidth, targetHeight) = calculateTargetDimensions(
            originalOptions.outWidth,
            originalOptions.outHeight
        )

        // 3. 计算采样率
        val sampleSize = calculateSampleSize(
            originalOptions,
            targetWidth,
            targetHeight
        )
        return Triple(targetWidth, targetHeight, sampleSize)
    }

    abstract fun provideInputStream(context: Context, source:Any): InputStream?

    /**
     * 计算目标缩放尺寸（按比例适配最大宽高）
     */
    private fun calculateTargetDimensions(
        width: Int,
        height: Int
    ): Pair<Int, Int> {
        var w = width.toFloat()
        var h = height.toFloat()
        val imgRatio = w / h
        val maxRatio = maxWidth / maxHeight

        if (h > maxHeight || w > maxWidth) {
            if (imgRatio < maxRatio) {
                val scale = maxHeight / h
                w = scale * w
                h = maxHeight
            } else if (imgRatio > maxRatio) {
                val scale = maxWidth / w
                h = scale * h
                w = maxWidth
            } else {
                w = maxWidth
                h = maxHeight
            }
        }
        return w.roundToInt() to h.roundToInt()
    }

    /**
     * 计算采样率（避免加载完整大图导致OOM）
     */
    private fun calculateSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSample = 1
        val (origW, origH) = options.outWidth to options.outHeight

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

    /**
     * 解码Bitmap（带采样率）
     */
    protected fun decodeBitmap(
        inputStreamProvider: () -> InputStream?,
        sampleSize: Int
    ): Bitmap? {
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inJustDecodeBounds = false
            inTempStorage = ByteArray(tempBufferSize)
        }
        return inputStreamProvider()?.use { stream ->
            BitmapFactory.decodeStream(stream, null, opts)
        }
    }

    /**
     * Bitmap 缩放扩展函数
     */
    protected fun Bitmap.scaleTo(width: Int, height: Int): Bitmap {
        val scaled = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        val ratioX = width / this.width.toFloat()
        val ratioY = height / this.height.toFloat()
        val midX = width / 2f
        val midY = height / 2f

        val matrix = Matrix().apply { setScale(ratioX, ratioY, midX, midY) }
        Canvas(scaled).apply {
            setMatrix(matrix)
            drawBitmap(
                this@scaleTo,
                midX - this@scaleTo.width / 2f,
                midY - this@scaleTo.height / 2f,
                Paint(Paint.FILTER_BITMAP_FLAG)
            )
        }
        return scaled
    }

    /**
     * 根据Exif信息旋转Bitmap（适配File输入）
     */
    protected fun Bitmap.rotateByExif(file: File): Bitmap {
        return runCatching {
            FileInputStream(file).use { stream ->
                val exif = ExifInterface(stream)
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
                Bitmap.createBitmap(this, 0, 0, width, height, rotateMatrix, true)
            }
        }.getOrDefault(this)
    }

    /**
     * 根据文件大小选择压缩质量
     */
    private fun chooseQuality(fileSize: Long): Int {
        return when {
            fileSize > 5_000_000 -> 40    // >5MB → 40%质量
            fileSize > 2_000_000 -> 50    // >2MB → 50%质量
            fileSize > 1_000_000 -> 60    // >1MB → 60%质量
            fileSize > 500_000 -> 70      // >500KB → 70%质量
            fileSize > 250_000 -> 80      // >250KB → 80%质量
            else -> 90                    // ≤250KB → 90%质量
        }
    }

    /**
     * 创建压缩后的输出文件
     */
    private fun createOutputFile(context: Context): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val randomSuffix = Random.nextInt(100_000, 1_000_000)
        val fileName = "IMG_${timestamp}_${randomSuffix}.jpg"
        val dir = File(context.getExternalFilesDir(null), "Pictures").apply {
            if (!exists()) mkdirs()
        }
        return File(dir, fileName)
    }

    /**
     * 核心压缩方法（子类实现）
     */
    abstract suspend fun compress(context: Context, source: Any): String?

    abstract fun fileSize(context:Context, source:Any) : Long

    protected fun saveFile(context: Context, source: Any, bitmap: Bitmap): String {
        // 5. 查询文件大小
        val fileSize = fileSize(context, source)
        val quality = chooseQuality(fileSize)

        // 6. 写入输出文件
        val outputFile = createOutputFile(context)
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }

        // 释放Bitmap内存
        if (!bitmap.isRecycled) {
            bitmap.recycle()
        }

        return outputFile.absolutePath
    }
}