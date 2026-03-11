package com.au.module_imagecompressed.loader

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import java.io.File

/**
 * 修改为android内置参数
 */
val SYS_MIN_SIZE = Size(512, 384)
/**
 * 修改为android内置参数
 */
val SYS_FULL_SIZE = Size(1024, 786)

val LOW_SIZE = Size(240, 320)
val MID_SIZE = Size(480, 640)

/**
 * 加载原始图片，忽略压缩配置
 */
suspend fun loadOriginalUriOrFile(context:Context, uriOrFile: Any, originalLoadConfig: ImageLoadConfig = ImageLoadConfig(
    quality = ImageLoadConfig.Quality.MEMORY_POLICY_DEFAULT,
    alwaysLoadOriginal = true
)) : Bitmap?{
    return when (uriOrFile) {
        is Uri -> ImageLoadHelper.loadImage(context, uriOrFile, originalLoadConfig)
        is File -> ImageLoadHelper.loadImage(uriOrFile, originalLoadConfig)
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
    quality = ImageLoadConfig.Quality.MEMORY_POLICY_LOW_RAM,
    ignoreSizeInKB = 2000,
)) : Bitmap? {
    return when(uriOrFile) {
        is Uri -> ImageLoadHelper.loadImage(context, uriOrFile, compressConfig)
        is File -> ImageLoadHelper.loadImage(uriOrFile, compressConfig)
        else -> null
    }
}

/**
 * 加载缩略图
 * @param thumbSize 缩略图尺寸
 */
suspend fun loadThumbnailUriOrFile(context:Context,
                                 uriOrFile: Any,
                                 thumbSize: Size?) : Bitmap? {
    if (thumbSize == null) {
        return loadCompressUriOrFile(context, uriOrFile)
    }

    return when (uriOrFile) {
        is Uri -> ThumbnailLoadHelper.loadThumbnailCompat(uriOrFile, thumbSize)
        is File -> ThumbnailLoadHelper.createImageThumbnailByPath(uriOrFile.absolutePath, thumbSize)
        else -> null
    }
}