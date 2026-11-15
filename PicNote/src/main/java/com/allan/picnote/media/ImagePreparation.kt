package com.allan.picnote.media

import android.content.Context
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import top.yayalu.imagecodec.ImageCodec
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

const val ImportedImageWebpQuality = 90

data class PreparedImage(
    val sourceUri: String,
    val file: File,
    val hash: String,
    val width: Int,
    val height: Int,
    val queryVolume: String? = null,
    val queryVersion: String? = null,
)

/** 在仓库的串行 IO 区间内准备 WebP；原始字节仅用于哈希和带方向的系统解码。 */
class ImagePreparation(private val context: Context) {
    suspend fun prepare(raw: String, directory: File): PreparedImage {
        check(directory.isDirectory || directory.mkdirs()) { "无法创建图片草稿目录" }
        val token = UUID.randomUUID().toString()
        val original = File(directory, ".$token.source")
        val output = File(directory, "$token.webp")
        var complete = false
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(Uri.parse(raw))?.use { input ->
                FileOutputStream(original).use { stream ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                        stream.write(buffer, 0, count)
                    }
                }
            } ?: error("无法读取图片，请重新选择")
            currentCoroutineContext().ensureActive()
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(original)) { decoder, info, _ ->
                require(info.size.width <= 16383 && info.size.height <= 16383) { "图片宽高不能超过 16383 像素，请选择其他图片" }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
            }
            try {
                currentCoroutineContext().ensureActive()
                val bytes = try {
                    val options = ImageCodec.Options().apply {
                        format = ImageCodec.FORMAT_WEBP
                        quality = ImportedImageWebpQuality
                        scalePercent = 100.0
                        webpLossless = false
                    }
                    ImageCodec.encode(bitmap, options)
                }
                catch (e: LinkageError) { throw IllegalStateException("图片压缩组件不可用，当前安装包仅支持 ARM 设备", e) }
                currentCoroutineContext().ensureActive()
                require(bytes.isNotEmpty()) { "图片压缩失败，请重新选择" }
                FileOutputStream(output).use { it.write(bytes); it.fd.sync() }
                currentCoroutineContext().ensureActive()
                complete = true
                return PreparedImage(raw, output, digest.digest().joinToString("") { "%02x".format(it) }, bitmap.width, bitmap.height)
            } finally { bitmap.recycle() }
        } catch (e: OutOfMemoryError) {
            throw IllegalStateException("图片过大，内存不足，无法处理", e)
        } finally {
            original.delete()
            if (!complete) output.delete()
        }
    }
}
