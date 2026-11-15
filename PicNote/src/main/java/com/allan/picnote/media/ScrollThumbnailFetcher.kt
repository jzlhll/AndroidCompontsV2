package com.allan.picnote.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.allowRgb565
import coil3.request.bitmapConfig
import coil3.size.Precision
import coil3.size.Scale
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.toBitmap
import com.allan.picnote.logEx
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okio.buffer
import java.io.File
import java.io.FileNotFoundException

data class ScrollThumbnail(val file: File, val key: String, val cacheOnly: Boolean = false)

/** 从正式图片生成滚动缩略图，磁盘文件与解码后的内存缓存均交给 Coil 管理。 */
class ScrollThumbnailFetcher(
    private val data: ScrollThumbnail,
    private val options: Options,
    private val imageLoader: ImageLoader,
) : Fetcher {
    override suspend fun fetch(): ImageFetchResult = withContext(Dispatchers.IO) {
        val cache = imageLoader.diskCache
        cache?.openSnapshot(data.key)?.use { snapshot ->
            val result = imageLoader.execute(decodeRequest(snapshot.data.toFile()))
            if (result is SuccessResult) return@withContext ImageFetchResult(result.image, false, DataSource.DISK)
        }
        cache?.remove(data.key)
        if (data.cacheOnly) throw FileNotFoundException("Scroll thumbnail is not cached")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(data.file.absolutePath, bounds)
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Cannot read saved image dimensions" }
        val sourceLongSide = if (bounds.outWidth > bounds.outHeight) bounds.outWidth else bounds.outHeight
        var longSide = sourceLongSide / 20
        if (longSide < 32) longSide = 32
        val result = imageLoader.execute(decodeRequest(data.file).newBuilder().size(longSide, longSide).build())
        if (result !is SuccessResult) throw (result as coil3.request.ErrorResult).throwable
        currentCoroutineContext().ensureActive()
        val editor = cache?.openEditor(data.key)
        if (cache != null && editor != null) {
            try {
                cache.fileSystem.write(editor.metadata) { writeUtf8("picnote-scroll-thumbnail-v1") }
                cache.fileSystem.sink(editor.data).buffer().outputStream().use { output ->
                    @Suppress("DEPRECATION")
                    check(result.image.toBitmap().compress(Bitmap.CompressFormat.WEBP, 90, output)) { "Cannot encode scroll thumbnail" }
                }
                currentCoroutineContext().ensureActive()
                editor.commit()
            } catch (e: Exception) {
                runCatching { editor.abort() }
                if (e is CancellationException) throw e
                logEx(throwable = e, message = "Write scroll thumbnail cache failed")
            }
        }
        ImageFetchResult(result.image, false, DataSource.DISK)
    }

    private fun decodeRequest(file: File): ImageRequest = ImageRequest.Builder(options.context)
        .data(file)
        .bitmapConfig(Bitmap.Config.RGB_565)
        .allowRgb565(true)
        .allowHardware(false)
        .scale(Scale.FIT)
        .precision(Precision.EXACT)
        .memoryCachePolicy(CachePolicy.DISABLED)
        .diskCachePolicy(CachePolicy.DISABLED)
        .build()

    class Factory : Fetcher.Factory<ScrollThumbnail> {
        override fun create(data: ScrollThumbnail, options: Options, imageLoader: ImageLoader): Fetcher =
            ScrollThumbnailFetcher(data, options, imageLoader)
    }
}
