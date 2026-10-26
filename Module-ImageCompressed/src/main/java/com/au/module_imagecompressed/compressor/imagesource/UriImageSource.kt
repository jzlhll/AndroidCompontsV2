package com.au.module_imagecompressed.compressor.imagesource

import android.content.ContentResolver
import android.graphics.ImageDecoder
import android.net.Uri
import com.au.module_android.utilsmedia.UriParsedInfo
import com.au.module_android.utilsmedia.myParse
import java.io.InputStream

/**
 * Uri 图片源，集中处理 Provider 流、元数据和系统解码源。
 *
 * @param uri 调用方须先通过 resolveUploadUri 处理原始 EXIF 访问标记。
 * @param parsedInfo 已有的解析结果，用于避免重复查询；未传入时按需解析并缓存。
 */
class UriImageSource(
    private val contentResolver: ContentResolver,
    private val uri: Uri,
    private val parsedInfo: UriParsedInfo? = null,
) : IImageSource {
    private val info by lazy { parsedInfo ?: uri.myParse(contentResolver) }

    override val fileSize: Long
        get() = info.fileLength

    override val extension: String
        get() = info.extension.ifEmpty { info.name.substringAfterLast('.', "") }

    override fun openInputStream(): InputStream? = contentResolver.openInputStream(uri)

    override fun createImageDecodeSource(): ImageDecoder.Source = ImageDecoder.createSource(contentResolver, uri)
}
