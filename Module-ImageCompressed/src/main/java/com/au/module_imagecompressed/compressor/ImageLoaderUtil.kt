package com.au.module_imagecompressed.compressor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.au.module_android.utilsmedia.myParse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 主要功能是，直接通过ImageLoader类或者BitmapFactory类 根据 Config 来进行图片加载
 */
internal class ImageLoaderUtil(
    val config: ImageLoadConfig,
) {
    private lateinit var api: IApi
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

            val loader = ImageLoaderUtil(config = config)
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

            val loader = ImageLoaderUtil(config = config)
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

    private interface IApi {
        fun decodeBitmap(): Bitmap?
    }

//    private class OldApi(val config: Config, private val provideInputStream: () -> InputStream?) : IApi {
//        override fun decodeBitmap(): Bitmap? {
//            // 1. Decode bounds
//            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
//            provideInputStream()?.use { stream ->
//                BitmapFactory.decodeStream(stream, null, options)
//            }
//
//            // 2. Calculate sample size
//            val sampleSize = CompressUtil.calculateSampleSize(
//                options.outWidth,
//                options.outHeight,
//                config.maxWidth,
//                config.maxHeight
//            )
//
//            // 3. Config
//            val configType = if (config.quality == Quality.High) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
//
//            // 4. Decode
//            val opts = BitmapFactory.Options().apply {
//                inSampleSize = sampleSize
//                inJustDecodeBounds = false
//                inPreferredConfig = configType
//            }
//            return provideInputStream()?.use { stream ->
//                BitmapFactory.decodeStream(stream, null, opts)
//            }
//        }
//    }

    private class NewApi(val config: ImageLoadConfig, val provideImageDecodeSource: () -> ImageDecoder.Source?) : IApi {
        override fun decodeBitmap(): Bitmap? {
            return try {
                val source = provideImageDecodeSource() ?: return null
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val originalWidth = info.size.width
                    val originalHeight = info.size.height

                    val sampleSize = CompressUtil.calculateSampleSize(
                        originalWidth,
                        originalHeight,
                        config.maxWidth,
                        config.maxHeight
                    )
                    decoder.setTargetSampleSize(sampleSize)
                    //todo decoder.crop = Rect()

                    if (config.quality == ImageLoadQuality.High) {
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