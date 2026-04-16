/* 
* Created by jiangzhonglun@imagecho.ai on 2026/04/16.
*
* Copyright (C) 2026 [imagecho.ai]. All Rights Reserved.
*
* This software is proprietary and confidential. Unauthorized use, copying,
* modification, or distribution is prohibited without prior written consent.
*
* For inquiries, contact: [contacts@imagecho.ai]
*/

package com.au.module_imagecompressed.compressor

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import com.au.module_android.utilsmedia.myParse
import java.io.File
import java.util.Locale

internal class SizeParseUtil {

    private class CancelImageDecodeException : RuntimeException("Cancel image size decoder")

    fun decodeImagePixelSize(file: File): Pair<Int, Int>? {
        val fileExtension = file.extension.lowercase(Locale.ROOT)
        if (shouldUseBitmapFactoryFirst(fileExtension)) {
            decodeImagePixelSizeByBitmapFactory(file)?.let {
                return it
            }
        }
        return tryDecodeImagePixelSizeByImageDecoder {
            decodeImagePixelSizeFromSource(ImageDecoder.createSource(file))
        }
    }

    fun decodeImagePixelSize(context: Context, uri: Uri): Pair<Int, Int>? {
        val uriParseInfo = uri.myParse(context.contentResolver)
        val extension = uriParseInfo.extension
        if (shouldUseBitmapFactoryFirst(extension)) {
            decodeImagePixelSizeByBitmapFactory(context, uri)?.let {
                return it
            }
        }
        return tryDecodeImagePixelSizeByImageDecoder {
            decodeImagePixelSizeFromSource(
                ImageDecoder.createSource(context.contentResolver, uri)
            )
        }
    }

    // 常规静态图优先走 bounds 读取，避免不必要的真实解码。
    private fun shouldUseBitmapFactoryFirst(extension: String): Boolean {
        return extension == "jpg" ||
                extension == "jpeg" ||
                extension == "png" ||
                extension == "webp"
    }

    // 使用 BitmapFactory 仅解析文件头，读取本地文件像素宽高。
    private fun decodeImagePixelSizeByBitmapFactory(file: File): Pair<Int, Int>? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
            options.outWidth.takeIf { it > 0 }?.let { width ->
                options.outHeight.takeIf { it > 0 }?.let { height ->
                    width to height
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    // 使用 BitmapFactory 仅解析文件头，读取 Uri 像素宽高。
    private fun decodeImagePixelSizeByBitmapFactory(context: Context, uri: Uri): Pair<Int, Int>? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }
            options.outWidth.takeIf { it > 0 }?.let { width ->
                options.outHeight.takeIf { it > 0 }?.let { height ->
                    width to height
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    // 统一兜底到 ImageDecoder，兼容 heic/heif 等较新的格式。
    private inline fun tryDecodeImagePixelSizeByImageDecoder(block: () -> Pair<Int, Int>?): Pair<Int, Int>? {
        return try {
            block()
        } catch (_: Throwable) {
            null
        }
    }

    // 读取到头部尺寸后立即中断，避免继续执行真实位图解码。
    private fun decodeImagePixelSizeFromSource(source: ImageDecoder.Source): Pair<Int, Int>? {
        var width = 0
        var height = 0
        try {
            ImageDecoder.decodeBitmap(source) { _, info, _ ->
                width = info.size.width
                height = info.size.height
                throw CancelImageDecodeException()
            }
        } catch (_: Throwable) {
            //ignored
        }
        return if (width > 0 && height > 0) width to height else null
    }
}