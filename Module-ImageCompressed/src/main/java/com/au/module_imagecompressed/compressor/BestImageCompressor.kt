package com.au.module_imagecompressed.compressor

import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import com.au.module_android.log.logEx
import com.au.module_imagecompressed.compressor.imagesource.IImageSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * 图片压缩入口，按顺序执行采样解码、尺寸缩放和质量编码，并统一管理 Bitmap 的回收。
 *
 * @param source 原图数据源，统一提供输入流、文件大小、扩展名和系统解码源。
 */
class BestImageCompressor(
    private val source: IImageSource,
    val config: Config,
) {
    companion object {
        private const val TAG = "ImageProcess"
        const val DEFAULT_IGNORE_KB = 150
        /** 可直接上传的常见图片格式，供原文件跳过列表和特殊格式转换判断共用。 */
        val COMMON_IMAGE_FILE_TYPES = setOf("jpg", "jpeg", "png", "heic")
        private val MOTION_PHOTO_FILE_TYPES = setOf("jpg", "jpeg", "heic", "heif", "avif")
    }

    /** 统一配置尺寸、编码质量、格式及跳过和回退规则。 */
    data class Config(
        /** 长边上限，默认 1920；null 或小于等于 0 时仅关闭尺寸采样和缩放，仍按配置质量编码。 */
        val scaleMaxSide: Int? = 1920,
        /** 编码质量，取值 0 至 100；推荐 92 轻度压缩、85 均衡压缩、75 深度压缩。 */
        val quality: Int = 85,
        /** 目标编码格式，默认 JPEG（.jpg）；也可选择 PNG 或系统支持的 WebP 格式 */
        val outputFormat: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        /** 默认不处理的格式，忽略大小写，优先跳过压缩和格式转换。 */
        val ignoreFileTypes: List<String> = listOf("gif", "svg", "ico"),
        /** 小于此 KB 阈值时跳过压缩 */
        val ignoreSizeInKB: Int = DEFAULT_IGNORE_KB,
        /**
         * 压缩失败或跳过后的回退策略：
         * all / onlyUri 将 Content Uri 拷贝到缓存，已有本地文件直接返回；none / null 返回 null。
         */
        val alwaysCopyMode: String? = "none",
        /** 进入压缩流程后是否携带 Motion Photo；true 完整复制动态照片，false 生成静态图。 */
        val keepMotionPhoto: Boolean = false,
        /** 直接保留原文件的格式，不带点且忽略大小写，不受小图阈值和不常见格式转换策略影响。 */
        val skipCompressionFileTypes: Set<String> = emptySet(),
    ) {
        /** 忽略列表和原文件跳过列表中的格式都不进入压缩流程。 */
        fun isCompressionEnabledFor(extension: String): Boolean =
            ignoreFileTypes.none { it.equals(extension, ignoreCase = true) } &&
                skipCompressionFileTypes.none { it.equals(extension, ignoreCase = true) }
    }

    private val sourceExif: ExifInterface by lazy {
        source.openInputStream()?.use { ExifInterface(it) }
            ?: throw IOException("Cannot open image source")
    }

    private val scaleMaxSide = config.scaleMaxSide?.takeIf { it > 0 }
    private val decoder = ImageBitmapDecoder(
        source = source,
        scaleMaxSide = scaleMaxSide,
    )
    private val scalePipeline = ImageScalePipeline(
        scaleMaxSide = scaleMaxSide,
        outputFormat = config.outputFormat,
    )
    private val qualityPipeline = ImageQualityCompressPipeline(source, config)

    /** 在 IO 线程读取受支持容器的 XMP，识别 Motion Photo 或旧版 MicroVideo 标记。 */
    suspend fun hasMotionPhoto(): Boolean = withContext(Dispatchers.IO) {
        source.extension.lowercase() in MOTION_PHOTO_FILE_TYPES && sourceExif.hasMotionPhotoMetadata()
    }

    /** 按配置生成静态压缩图，或完整复制需要携带的 Motion Photo；格式策略跳过或处理失败时返回 null。 */
    suspend fun compress(): File? {
        return withContext(Dispatchers.IO) {
            var outputFile: File? = null
            var completed = false
            try {
                currentCoroutineContext().ensureActive()
                if (!config.isCompressionEnabledFor(source.extension)) return@withContext null
                if (config.keepMotionPhoto && hasMotionPhoto()) {
                    // 完整复制原容器，保留视频、XMP 及其与静态图的关联。
                    val copied = CompressCacheConstManager.createCompressOutputFile(source.extension)
                    val temporary = File(copied.parentFile, "${copied.name}.part")
                    outputFile = temporary
                    val input = source.openInputStream() ?: throw IOException("Cannot open Motion Photo source")
                    val copiedBytes = input.use { stream -> temporary.outputStream().use { stream.copyTo(it) } }
                    currentCoroutineContext().ensureActive()
                    check(copiedBytes > 0L && copiedBytes == source.fileSize) {
                        "Incomplete Motion Photo copy"
                    }
                    CompressCacheConstManager.publish(temporary, copied)
                    completed = true
                    return@withContext copied
                }
                val extension = when (config.outputFormat) {
                    Bitmap.CompressFormat.JPEG -> "jpg"
                    Bitmap.CompressFormat.PNG -> "png"
                    else -> "webp"
                }
                val encoded = CompressCacheConstManager.createCompressOutputFile(extension)
                val temporary = File(encoded.parentFile, "${encoded.name}.part")
                outputFile = temporary
                val decoded = decoder.decodeBitmap(sourceExif)
                var bitmap = decoded.bitmap ?: return@withContext null
                try {
                    currentCoroutineContext().ensureActive()
                    val scaledBitmap = scalePipeline.process(bitmap)
                    if (scaledBitmap !== bitmap) {
                        bitmap.recycle()
                        bitmap = scaledBitmap
                    }
                    qualityPipeline.process(
                        bitmap = bitmap,
                        outputFile = temporary,
                        sourceExif = sourceExif,
                        orientation = decoded.orientation,
                        originalWidth = decoded.originalW,
                        originalHeight = decoded.originalH,
                    )
                    currentCoroutineContext().ensureActive()
                    // EXIF 写回完成并关闭所有写入资源后，才通过同目录改名公开成品。
                    CompressCacheConstManager.publish(temporary, encoded)
                    completed = true
                    encoded
                } finally {
                    bitmap.recycle()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logEx(TAG, throwable = e) { "compress failed" }
                null
            } finally {
                if (!completed) outputFile?.let { CompressCacheConstManager.delete(it) }
            }
        }
    }
}
