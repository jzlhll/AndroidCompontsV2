package com.au.module_imagecompressed.compressor

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import java.io.File

val LOW_SIZE = Size(240, 320)
val MID_SIZE = Size(480, 640)

/**
 * 加载原始图片，忽略压缩配置
 */
suspend fun loadOriginalUriOrFile(context:Context, uriOrFile: Any, originalLoadConfig: ImageLoadConfig = ImageLoadConfig(
    quality = ImageLoadQuality.High,
    alwaysLoadOriginal = true
)) : Bitmap?{
    return when (uriOrFile) {
        is Uri -> ImageLoaderUtil.loadImage(context, uriOrFile, originalLoadConfig)
        is File -> ImageLoaderUtil.loadImage(uriOrFile, originalLoadConfig)
        else -> null
    }
}

/**
 * 加载压缩图片
 */
suspend fun loadCompressUriOrFile(context:Context,
                                  uriOrFile: Any,
                                  compressConfig: ImageLoadConfig = ImageLoadConfig(
    maxWidth = 1440,
    maxHeight = 1920,
    quality = ImageLoadQuality.Low,
    ignoreSizeInKB = 2000,
)) : Bitmap? {
    return when(uriOrFile) {
        is Uri -> ImageLoaderUtil.loadImage(context, uriOrFile, compressConfig)
        is File -> ImageLoaderUtil.loadImage(uriOrFile, compressConfig)
        else -> null
    }
}

/**
 * 加载缩略图
 * @param thumbSize 缩略图尺寸 推荐使用 LOW_SIZE 或 MID_SIZE
 */
suspend fun loadThumbnailUriOrFile(context:Context,
                                 uriOrFile: Any,
                                 thumbSize: Size?) : Bitmap? {
    if (thumbSize == null) {
        return loadCompressUriOrFile(context, uriOrFile)
    }

    val thumbnailUtils = ThumbnailCompatUtil(context)
    return when (uriOrFile) {
        is Uri -> thumbnailUtils.loadThumbnailCompat(uriOrFile, thumbSize)
        is File -> thumbnailUtils.createImageThumbnailByPath(uriOrFile.absolutePath, thumbSize)
        else -> null
    }
}