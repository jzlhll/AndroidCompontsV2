package com.allan.picnote.media

import android.content.Context
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.net.Uri
import com.allan.picnote.R
import com.allan.picnote.data.PicNoteStorageException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import top.yayalu.imagecodec.ImageCodec
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

const val ImportedImageWebpQuality = 90
private const val WebpMaxDimension = 16383
private const val MaxImportPixels = 16_000_000L
private const val EstimatedBytesPerPixel = 20L

data class PreparedImage(
    val sourceUri: String,
    val file: File,
    val hash: String,
    val width: Int,
    val height: Int,
    val sourceStamp: MediaSourceStamp? = null,
)

/** 在仓库的串行 IO 区间内准备 WebP；原始字节仅用于哈希和带方向的系统解码。 */
class ImagePreparation(private val context: Context) {
    suspend fun prepare(raw: String, directory: File): PreparedImage {
        if (!directory.isDirectory && !directory.mkdirs()) throw PicNoteStorageException(R.string.error_create_image_draft_dir)
        val token = UUID.randomUUID().toString()
        val original = File(directory, ".$token.source")
        val output = File(directory, "$token.webp")
        val temporary = File(directory, ".$token.tmp")
        var complete = false
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val source = try {
                context.contentResolver.openInputStream(Uri.parse(raw))
            } catch (e: FileNotFoundException) {
                throw PicNoteStorageException(R.string.error_read_image, e)
            } catch (e: SecurityException) {
                throw PicNoteStorageException(R.string.error_read_image, e)
            } ?: throw PicNoteStorageException(R.string.error_read_image)
            source.use { input ->
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
            }
            currentCoroutineContext().ensureActive()
            val bitmap = try {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(original)) { decoder, info, _ ->
                    val width = info.size.width
                    val height = info.size.height
                    if (width <= 0 || height <= 0) throw PicNoteStorageException(R.string.error_read_image)
                    // 为解码、原生压缩和编码结果留出同一份预算，正常尺寸继续使用原始分辨率。
                    val pixels = width.toLong() * height
                    val budget = min(MaxImportPixels, Runtime.getRuntime().maxMemory() / 3 / EstimatedBytesPerPixel)
                    val ratio = min(sqrt(budget.toDouble() / pixels), WebpMaxDimension.toDouble() / max(width, height))
                    if (ratio < 1.0) decoder.setTargetSize(max(1, (width * ratio).toInt()), max(1, (height * ratio).toInt()))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                }
            } catch (e: ImageDecoder.DecodeException) {
                throw PicNoteStorageException(R.string.error_read_image, e)
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
                    withContext(Dispatchers.Default) { ImageCodec.encode(bitmap, options) }
                }
                catch (e: LinkageError) { throw PicNoteStorageException(R.string.error_image_codec_unavailable, e) }
                currentCoroutineContext().ensureActive()
                if (bytes.isEmpty()) throw PicNoteStorageException(R.string.error_image_compression)
                FileOutputStream(temporary).use { stream ->
                    stream.write(bytes)
                    stream.fd.sync()
                }
                currentCoroutineContext().ensureActive()
                Files.move(temporary.toPath(), output.toPath(), StandardCopyOption.ATOMIC_MOVE)
                currentCoroutineContext().ensureActive()
                complete = true
                return PreparedImage(raw, output, digest.digest().joinToString("") { "%02x".format(it) }, bitmap.width, bitmap.height)
            } finally { bitmap.recycle() }
        } catch (e: OutOfMemoryError) {
            throw PicNoteStorageException(R.string.error_image_out_of_memory, e)
        } finally {
            original.delete()
            temporary.delete()
            if (!complete) output.delete()
        }
    }
}
