package com.au.module_imagecompressed.compressor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import com.au.module_android.utilsmedia.myParse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

class ImageLoader(
    val config: Config,
) {
    private lateinit var api: IApi
    companion object {
        const val DEFAULT_IGNORE_KB = 1200

        suspend fun loadImage(context: Context, uri: Uri, config: Config = Config()): Bitmap? {
            val parsedInfo = uri.myParse(context)
            val ignore = parsedInfo.fileLength < config.ignoreSizeInKB * 1024 ||
                    config.ignoreFileTypes.contains(parsedInfo.extension.lowercase())

            if (ignore) {
                // Load original
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    try {
                        val source = ImageDecoder.createSource(context.contentResolver, uri)
                        return ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        }
                    } catch (_: Exception) {
                        return null
                    }
                } else {
                    return context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                }
            }

            val loader = ImageLoader(config = config)
            loader.api = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                NewApi(config) {
                    ImageDecoder.createSource(context.contentResolver, uri)
                }
            } else {
                OldApi(config) {
                    context.contentResolver.openInputStream(uri)
                }
            }

            return loader.load()
        }

    }

    data class Config(
        /** 最大边长限制，防止内存溢出，也能显著降低图片大小 */
        val maxWidth: Int = 1440,
        /** 最大边长限制，防止内存溢出，也能显著降低图片大小 */
        val maxHeight: Int = 1920,
        /**  压缩质量选择，默认；可选，deep，压缩的更狠；shallow，比默认轻微压缩。*/
        val qualityType:String = "default",
        /**
         * 忽略列表，不做压缩
         */
        val ignoreFileTypes:List<String> = listOf("gif", "webp", "svg"),
        /**
         * 多少kb就忽略
         */
        val ignoreSizeInKB : Int = DEFAULT_IGNORE_KB
    )

    suspend fun load(): Bitmap? {
        return withContext(Dispatchers.IO) {
            api.decodeBitmap()
        }
    }

    private interface IApi {
        fun decodeBitmap(): Bitmap?
    }

    private class OldApi(val config: Config, private val provideInputStream: () -> InputStream?) : IApi {
        override fun decodeBitmap(): Bitmap? {
            // 1. Decode bounds
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            provideInputStream()?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            // 2. Calculate sample size
            val sampleSize = CompressUtil.calculateSampleSize(
                options.outWidth,
                options.outHeight,
                config.maxWidth,
                config.maxHeight
            )

            // 3. Config
            val configType = if (config.qualityType == "shallow")
                Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565

            // 4. Decode
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inJustDecodeBounds = false
                inPreferredConfig = configType
            }
            return provideInputStream()?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private class NewApi(val config: Config, val provideImageDecodeSource: () -> ImageDecoder.Source?) : IApi {
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

                    if (config.qualityType == "shallow" || config.qualityType == "default") {
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