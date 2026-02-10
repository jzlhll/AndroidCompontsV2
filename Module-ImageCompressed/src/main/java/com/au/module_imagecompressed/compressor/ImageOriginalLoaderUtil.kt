package com.au.module_imagecompressed.compressor

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import java.io.File

/**
 * ThumbnailCompatUtil 与 ImageLoaderUtil 混合加载器
 *
 * ThumbnailCompatUtil用于加载缩略图，目前只是设计了2种低尺寸的图，故而是用来加载小小图的
 * ImageLoaderUtil 用于加载接近原始图片(限制在常用的清晰范围1920*1440, 避免过大导致OOM)
 */
class ImageOriginalLoaderUtil(private val context: Context,
                              private val myCompressConfig: ImageLoadConfig = ImageLoadConfig(
        quality = ImageLoadQuality.High,
        alwaysLoadOriginal = true
    )) {
    suspend fun loadUri(uri: Uri) : Bitmap?{
        return ImageLoaderUtil.loadImage(context, uri, myCompressConfig)
    }

    suspend fun loadFile(file: File) : Bitmap?{
        return ImageLoaderUtil.loadImage(file, myCompressConfig)
    }
}