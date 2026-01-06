package com.au.module_imagecompressed.compress

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * 智能图片压缩器 - 支持ImageDecoder和BitmapFactory双引擎
 * 特性：
 * 1. Android 9+使用ImageDecoder，低版本使用BitmapFactory
 * 2. 渐进式质量压缩：每次递减5，直到满足大小或达到最低质量
 * 3. 支持JPEG、PNG、WebP格式，Android 9+额外支持HEIC
 */
class SmartImageCompressor {

    data class Config(
        val maxSizeInBytes: Int,           // 目标最大文件大小（字节）
        val minQuality: Int = 50,          // 允许的最低质量（1-100）
        val outputFormat: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        val maxDimension: Int = 1920       // 最大边长限制，防止内存溢出
    )

    data class InputUnion(
        val input: File? = null,
        val uri: Uri? = null,
        var inputStream: InputStream? = null
    )

    /**
     * 批量压缩方法
     */
    fun batchCompress(
        inputs: List<InputUnion>,
        outputDir: File,
        config: Config,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ) {
        inputs.forEachIndexed { index, input ->
            val outputFile = File(outputDir, "compressed_${System.currentTimeMillis()}_$index.jpg")

            compress(input, outputFile, config).onSuccess {
                onProgress(index + 1, inputs.size)
            }.onFailure { e ->
                Log.e("BatchCompress", "压缩失败: ${e.message}")
            }
        }
    }

    /**
     * 压缩入口方法
     */
    fun compress(input: InputUnion, outputFile: File, config: Config): Result<File> {
        return try {
            // 1. 解码图片（自动选择解码器）
            val bitmap = when {
                // Android 9+ 使用 ImageDecoder
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
                    decodeWithImageDecoder(input, config.maxDimension)
                }
                else -> {
                    // 低版本使用 BitmapFactory
                    decodeWithBitmapFactory(input, config.maxDimension)
                }
            }

            bitmap ?: return Result.failure(IllegalArgumentException("无法解码输入"))

            // 2. 应用压缩逻辑
            applyCompression(bitmap, outputFile, config)

            // 3. 清理内存
            bitmap.recycle()

            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 使用 ImageDecoder 解码（Android 9+）
     */
    @SuppressLint("NewApi")
    private fun decodeWithImageDecoder(input: Any, maxDimension: Int): Bitmap? {
        return try {
            val source = when (input) {
                is File -> ImageDecoder.createSource(input)
                else -> throw IllegalArgumentException("ImageDecoder仅支持File类型输入")
            }

            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                // 通过ImageInfo获取原始尺寸
                val originalWidth = info.size.width
                val originalHeight = info.size.height

                // 计算采样尺寸
                val sampleSize = calculateInSampleSize(originalWidth, originalHeight, maxDimension)
                decoder.setTargetSampleSize(sampleSize)
                Log.d("ImageDecoder", "原始尺寸: ${originalWidth}x${originalHeight}, " +
                        "采样率: $sampleSize, 目标尺寸: ${originalWidth/sampleSize}x${originalHeight/sampleSize}")
            }
        } catch (e: Exception) {
            Log.e("ImageDecoder", "解码失败: ${e.message}")
            null
        }
    }

    /**
     * 使用 BitmapFactory 解码（兼容所有版本）
     */
    private fun decodeWithBitmapFactory(input: Any, maxDimension: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                // 第一步：只解码边界信息
                inJustDecodeBounds = true

                when (input) {
                    is File -> BitmapFactory.decodeFile(input.absolutePath, this)
                    is InputStream -> {
                        input.mark(1024 * 1024)
                        BitmapFactory.decodeStream(input, null, this)
                        input.reset()
                    }
                    else -> null
                }

                // 计算采样率
                inSampleSize = calculateInSampleSize(outWidth, outHeight, maxDimension)

                // 第二步：实际解码图片
                inJustDecodeBounds = false
                inPreferredConfig = Bitmap.Config.RGB_565 // 节省内存
            }

            when (input) {
                is File -> BitmapFactory.decodeFile(input.absolutePath, options)
                is InputStream -> BitmapFactory.decodeStream(input, null, options)
                else -> null
            }
        } catch (e: Exception) {
            Log.e("BitmapFactory", "解码失败: ${e.message}")
            null
        }
    }


    /**
     * 渐进式质量压缩核心逻辑
     */
    private fun applyCompression(
        bitmap: Bitmap,
        outputFile: File,
        config: Config
    ) {
        var quality = 95 // 起始质量

        // PNG是无损格式，质量参数无效
        if (config.outputFormat == Bitmap.CompressFormat.PNG) {
            saveBitmap(bitmap, outputFile, config.outputFormat, 100)
            return
        }

        // 质量递减循环
        while (quality >= config.minQuality) {
            // 保存当前质量图片
            saveBitmap(bitmap, outputFile, config.outputFormat, quality)

            // 检查文件大小
            if (outputFile.length() <= config.maxSizeInBytes) {
                Log.d("Compression", "压缩成功: 质量=$quality, 大小=${outputFile.length() / 1024}KB")
                return
            }

            // 质量递减
            quality -= 5

            // 如果质量已经较低但文件仍然太大，尝试尺寸缩放
            if (quality < 70 && outputFile.length() > config.maxSizeInBytes * 1.5) {
                val scaledBitmap = scaleBitmap(bitmap, 0.8f)
                scaledBitmap?.let {
                    saveBitmap(it, outputFile, config.outputFormat, quality)
                    if (outputFile.length() <= config.maxSizeInBytes) {
                        Log.d("Compression", "压缩成功: 缩放+质量=$quality, 大小=${outputFile.length() / 1024}KB")
                        it.recycle()
                        return
                    }
                    it.recycle()
                }
            }
        }

        // 达到最低质量仍不满足，保存最低质量结果
        saveBitmap(bitmap, outputFile, config.outputFormat, config.minQuality)
        Log.d("Compression", "使用最低质量: ${config.minQuality}, 大小=${outputFile.length() / 1024}KB")
    }

    /**
     * 保存Bitmap到文件
     */
    private fun saveBitmap(
        bitmap: Bitmap,
        outputFile: File,
        format: Bitmap.CompressFormat,
        quality: Int
    ) {
        FileOutputStream(outputFile).use { fos ->
            bitmap.compress(format, quality, fos)
            fos.flush()
        }
    }

    /**
     * 缩放Bitmap（用于极端情况）
     */
    private fun scaleBitmap(bitmap: Bitmap, scaleFactor: Float): Bitmap? {
        if (scaleFactor >= 1.0f) return bitmap

        val newWidth = (bitmap.width * scaleFactor).toInt()
        val newHeight = (bitmap.height * scaleFactor).toInt()

        return try {
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } catch (e: OutOfMemoryError) {
            System.gc()
            null
        }
    }


    /**
     * 通用采样率计算方法
     */
    private fun calculateInSampleSize(
        originalWidth: Int,
        originalHeight: Int,
        maxDimension: Int,
        minDimension: Int = 100
    ): Int {
        // 如果图片已经小于等于最大限制，不需要采样
        if (originalWidth <= maxDimension && originalHeight <= maxDimension) {
            return 1
        }

        // 计算需要的缩放比例
        val widthRatio = originalWidth.toFloat() / maxDimension
        val heightRatio = originalHeight.toFloat() / maxDimension
        val maxRatio = maxOf(widthRatio, heightRatio)

        // 计算采样率（保持为2的幂次方更高效）
        var sampleSize = 1

        when {
            maxRatio > 8 -> sampleSize = 8
            maxRatio > 4 -> sampleSize = 4
            maxRatio > 2 -> sampleSize = 2
            // maxRatio在1-2之间时，sampleSize保持为1
        }

        // 保护措施：确保采样后不会太小
        if (originalWidth / sampleSize < minDimension || originalHeight / sampleSize < minDimension) {
            var adjustedSampleSize = sampleSize
            while (adjustedSampleSize > 1 &&
                (originalWidth / adjustedSampleSize < minDimension ||
                        originalHeight / adjustedSampleSize < minDimension)) {
                adjustedSampleSize /= 2
            }
            sampleSize = adjustedSampleSize
        }

        return sampleSize
    }
}