package com.au.module_imagecompressed.compressor

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import androidx.exifinterface.media.ExifInterface
import com.au.module_android.utilsmedia.UriParsedInfo
import com.au.module_android.utilsmedia.myParse
import java.io.File
import java.io.InputStream

internal class SizeParseUtil {

    private class CancelImageDecodeException : RuntimeException("Cancel image size decoder")

    fun resolveImageSize(
        context: Context,
        parsedInfo: UriParsedInfo,
        applyOrientation: Boolean = false
    ): Size? {
        val parsedSize = if (parsedInfo.isUriImage()) parsedInfo.size else null
        val rawSize = parsedSize ?: if (parsedInfo.isFile) {
            parsedInfo.file()?.let { decodeImagePixelSize(it) }
                ?: decodeImagePixelSizeByBitmapFactory(context, parsedInfo.uri)
                ?: tryDecodeImagePixelSizeByImageDecoder {
                    decodeImagePixelSizeFromSource(
                        ImageDecoder.createSource(context.contentResolver, parsedInfo.uri)
                    )
                }
        } else {
            decodeImagePixelSizeByBitmapFactory(context, parsedInfo.uri)
                ?: tryDecodeImagePixelSizeByImageDecoder {
                    decodeImagePixelSizeFromSource(
                        ImageDecoder.createSource(context.contentResolver, parsedInfo.uri)
                    )
                }
        }
        return rawSize?.let { applyOrientationIfNeeded(context, parsedInfo, it, applyOrientation) }
    }

    fun decodeImagePixelSize(file: File, applyOrientation: Boolean = false): Size? {
        val rawSize = decodeImagePixelSizeByBitmapFactory(file)
            ?: tryDecodeImagePixelSizeByImageDecoder {
                decodeImagePixelSizeFromSource(ImageDecoder.createSource(file))
            }
        if (!applyOrientation) {
            return rawSize
        }
        return rawSize?.let { size ->
            if (shouldSwapSizeByExif { file.inputStream() }) Size(size.height, size.width) else size
        }
    }

    fun decodeImagePixelSize(
        context: Context,
        uri: Uri,
        applyOrientation: Boolean = false
    ): Size? {
        val uriParseInfo = uri.myParse(context.contentResolver)
        return resolveImageSize(context, uriParseInfo, applyOrientation)
    }

    // 使用 BitmapFactory 仅解析文件头，读取本地文件像素宽高。
    private fun decodeImagePixelSizeByBitmapFactory(file: File): Size? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
            options.outWidth.takeIf { it > 0 }?.let { width ->
                options.outHeight.takeIf { it > 0 }?.let { height ->
                    Size(width, height)
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    // 使用 BitmapFactory 仅解析文件头，读取 Uri 像素宽高。
    private fun decodeImagePixelSizeByBitmapFactory(context: Context, uri: Uri): Size? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }
            options.outWidth.takeIf { it > 0 }?.let { width ->
                options.outHeight.takeIf { it > 0 }?.let { height ->
                    Size(width, height)
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    // 统一兜底到 ImageDecoder，兼容 heic/heif 等较新的格式。
    private inline fun tryDecodeImagePixelSizeByImageDecoder(block: () -> Size?): Size? {
        return try {
            block()
        } catch (_: Throwable) {
            null
        }
    }

    // 读取到头部尺寸后立即中断，避免继续执行真实位图解码。
    private fun decodeImagePixelSizeFromSource(source: ImageDecoder.Source): Size? {
        var width = 0
        var height = 0
        try {
            ImageDecoder.decodeBitmap(source) { _, info, _ ->
                width = info.size.width
                height = info.size.height
                throw CancelImageDecodeException()
            }
        } catch (_: Throwable) {
            // 忽略主动中断解码和异常来源，统一返回已读到的头部尺寸。
        }
        return if (width > 0 && height > 0) Size(width, height) else null
    }

    // 按系统方向列或 EXIF 方向按需交换宽高。
    private fun applyOrientationIfNeeded(
        context: Context,
        parsedInfo: UriParsedInfo,
        size: Size,
        applyOrientation: Boolean
    ): Size {
        if (!applyOrientation) {
            return size
        }
        var mediaOrientation: Int? = null
        if (parsedInfo.uri.authority == MediaStore.AUTHORITY) {
            try {
                context.contentResolver.query(
                    parsedInfo.uri,
                    arrayOf(MediaStore.Images.Media.ORIENTATION),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val orientationIndex = cursor.getColumnIndex(MediaStore.Images.Media.ORIENTATION)
                        mediaOrientation = if (orientationIndex == -1) null else cursor.getInt(orientationIndex)
                    }
                }
            } catch (_: Throwable) {
                mediaOrientation = null
            }
        }
        val shouldSwap = when (mediaOrientation) {
            90, 270 -> true
            0, 180 -> false
            else -> {
                val file = if (parsedInfo.isFile) parsedInfo.file() else null
                if (file != null) {
                    shouldSwapSizeByExif { file.inputStream() }
                } else {
                    shouldSwapSizeByExif { context.contentResolver.openInputStream(parsedInfo.uri) }
                }
            }
        }
        return if (shouldSwap) Size(size.height, size.width) else size
    }

    // 读取 EXIF orientation，判断展示尺寸是否需要交换宽高。
    private fun shouldSwapSizeByExif(provideInputStream: () -> InputStream?): Boolean {
        return try {
            provideInputStream()?.use {
                val orientation = ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED
                )
                orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
                        orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
                        orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
                        orientation == ExifInterface.ORIENTATION_TRANSVERSE
            } == true
        } catch (_: Throwable) {
            false
        }
    }
}