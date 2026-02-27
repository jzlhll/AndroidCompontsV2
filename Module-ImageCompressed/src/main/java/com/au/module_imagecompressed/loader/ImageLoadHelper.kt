package com.au.module_imagecompressed.loader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.au.module_android.utilsmedia.myParse
import com.au.module_imagecompressed.compressor.calculateSampleSize
import com.au.module_imagecompressed.compressor.calculateTargetDimensions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 主要功能是，直接通过ImageLoader类 来进行图片加载
 * BitmapFactory类 根据 Config 已经完全移除，采用android10以上。
 */
internal class ImageLoadHelper(
    val config: ImageLoadConfig,
) {
    private lateinit var api : NewApi
    companion object {
        const val DEFAULT_IGNORE_KB = 1200

        suspend fun loadImage(context: Context, uri: Uri, config: ImageLoadConfig = ImageLoadConfig()): Bitmap? {
            val parsedInfo = uri.myParse(context)
            val ignore = config.alwaysLoadOriginal || parsedInfo.fileLength < config.ignoreSizeInKB * 1024 ||
                    config.ignoreFileTypes.contains(parsedInfo.extension.lowercase())

            if (ignore) {
                // Load original
                try {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    return ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                } catch (_: Exception) {
                    return null
                }
            }

            val loader = ImageLoadHelper(config = config)
            loader.api = NewApi(config) {
                ImageDecoder.createSource(context.contentResolver, uri)
            }

            return loader.load()
        }

        suspend fun loadImage(file: File, config: ImageLoadConfig = ImageLoadConfig()): Bitmap? {
            val ignore = config.alwaysLoadOriginal || file.length() < config.ignoreSizeInKB * 1024 ||
                    config.ignoreFileTypes.contains(file.extension.lowercase())

            if (ignore) {
                // Load original
                try {
                    val source = ImageDecoder.createSource(file)
                    return ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.memorySizePolicy = ImageDecoder.MEMORY_POLICY_DEFAULT
                    }
                } catch (_: Exception) {
                    return null
                }
            }

            val loader = ImageLoadHelper(config = config)
            loader.api = NewApi(config) {
                ImageDecoder.createSource(file)
            }

            return loader.load()
        }

    }

    suspend fun load(): Bitmap? {
        return withContext(Dispatchers.IO) {
            api.decodeBitmap()
        }
    }

    private class NewApi(val config: ImageLoadConfig, val provideImageDecodeSource: () -> ImageDecoder.Source?) {
        fun decodeBitmap(): Bitmap? {
            return try {
                val source = provideImageDecodeSource() ?: return null
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val originalWidth = info.size.width
                    val originalHeight = info.size.height

                    val (targetWidth, targetHeight) = if (config.targetWidth > 0 && config.targetHeight > 0) {
                        // 使用固定尺寸
                        Pair(config.targetWidth, config.targetHeight)
                    } else {
                        // 使用最大尺寸限制
                        calculateTargetDimensions(
                            originalWidth,
                            originalHeight,
                            config.maxWidth.toFloat(),
                            config.maxHeight.toFloat()
                        )
                    }

                    val sampleSize = calculateSampleSize(
                        originalWidth,
                        originalHeight,
                        targetWidth,
                        targetHeight
                    )
                    decoder.setTargetSampleSize(sampleSize)
                    // 设置目标尺寸，确保最终图片大小为固定值
                    if (config.targetWidth > 0 && config.targetHeight > 0) {
                        decoder.setTargetSize(config.targetWidth, config.targetHeight)
                    }
                    //todo decoder.crop = Rect()

                    if (config.quality == ImageLoadConfig.Quality.MEMORY_POLICY_DEFAULT) {
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.memorySizePolicy = ImageDecoder.MEMORY_POLICY_DEFAULT
                    } else {
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.memorySizePolicy = ImageDecoder.MEMORY_POLICY_LOW_RAM
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}