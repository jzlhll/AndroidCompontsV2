package com.au.module_imagecompressed.compressor.imagesource

import android.graphics.ImageDecoder
import java.io.File
import java.io.InputStream

/** 本地文件图片源，集中处理文件流、文件大小和系统解码源。 */
class FileImageSource(private val file: File) : IImageSource {
    override val fileSize: Long by lazy { file.length() }

    override val extension: String
        get() = file.extension

    override fun openInputStream(): InputStream = file.inputStream()

    override fun createImageDecodeSource(): ImageDecoder.Source = ImageDecoder.createSource(file)
}
