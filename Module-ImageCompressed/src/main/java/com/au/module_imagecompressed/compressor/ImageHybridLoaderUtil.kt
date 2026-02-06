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
class ImageHybridLoaderUtil(private val context: Context) {
    companion object {
        val LOW_SIZE = Size(240, 320)
        val MID_SIZE = Size(480, 640)
    }

    private val thumbnailUtils = ThumbnailCompatUtil(context)

    private val myCompressConfig = ImageLoaderUtil.Config(
        maxWidth = 1440,
        maxHeight = 1920,
        qualityType = "deep",
        ignoreSizeInKB = 2000,
    )

    suspend fun loadUri(uri: Uri, size: Size?) : Bitmap?{
        if (size == null) {
            //加载原图
            //使用我的策略进行略微压缩加载避免过大
            return ImageLoaderUtil.loadImage(context, uri, myCompressConfig)
        }
        return thumbnailUtils.loadThumbnailCompat(uri, size)
    }

    suspend fun loadFile(file: File, size: Size?) : Bitmap?{
        if (size == null) {
            //加载原图
            //使用我的策略进行略微压缩加载避免过大
            return ImageLoaderUtil.loadImage(file, myCompressConfig)
        }
        return thumbnailUtils.createImageThumbnailByPath(file.absolutePath, size)
    }
}