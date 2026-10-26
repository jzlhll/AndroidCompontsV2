package com.au.module_imagecompressed.compressor

import android.content.Context
import android.net.Uri
import com.au.module_android.log.logdNoFile
import com.au.module_android.utilsmedia.UriParsedInfo
import com.au.module_android.utilsmedia.myParse
import com.au.module_android.utilsmedia.resolveUploadUri
import com.au.module_imagecompressed.compressor.BestImageCompressor.Config
import com.au.module_imagecompressed.compressor.imagesource.FileImageSource
import com.au.module_imagecompressed.compressor.imagesource.IImageSource
import com.au.module_imagecompressed.compressor.imagesource.UriImageSource
import java.io.File
import java.io.IOException

/**
 * 公开使用的函数
 * @param uri 传入 File 型或 Content 型的 Uri；调用方须先通过 [resolveUploadUri] 处理，本函数保留输入中可读取的 EXIF。
 * @throws IOException 动态照片处理失败时抛出，不按 alwaysCopyMode 回退到原图。
 */
suspend fun useCompress(
    context: Context,
    uri: Uri,
    config: Config = Config(),
): File? {
    val parsedInfo = uri.myParse(context)

    if (!parsedInfo.isUriImage()) {
        "".logdNoFile("ImageProcess") { "compression skipped: reason=not_image, uri=$uri" }
        return null
    }

    val source: IImageSource = if (parsedInfo.isFile) {
        FileImageSource(parsedInfo.file()!!)
    } else {
        UriImageSource(context.contentResolver, uri, parsedInfo)
    }

    if (!config.isCompressionEnabledFor(source.extension)) {
        return copy(config, context, uri, parsedInfo)
    }

    val compressor = BestImageCompressor(source = source, config = config)
    val hasMotionPhoto = compressor.hasMotionPhoto()
    // 保留动态内容时直接沿用原文件，只有要求复制时才生成完整副本。
    if (hasMotionPhoto && config.keepMotionPhoto) return copy(config, context, uri, parsedInfo)
    // 忽略格式已提前排除，动态照片和特殊格式仅绕过小图及收益检查。
    val alwaysCompress = hasMotionPhoto || BestImageCompressor.COMMON_IMAGE_FILE_TYPES.none {
        it.equals(source.extension, ignoreCase = true)
    }
    val beIgnore = !alwaysCompress && source.fileSize < config.ignoreSizeInKB * 1024

    var targetFile: File? = null
    if (!beIgnore) {
        //2. 开始压缩
        val compressed = compressor.compress()
        if (compressed != null) {
            val compressedSize = compressed.length()
            if (alwaysCompress || compressedSize <= source.fileSize) {
                // 始终压缩时保留成功产物，其余图片只接受不变大的结果。
                targetFile = compressed
            } else {
                //2.3 压缩变大了
                CompressCacheConstManager.delete(compressed)
            }
        }
    }

    if (hasMotionPhoto && targetFile == null) {
        throw IOException("Motion Photo processing failed: keepMotionPhoto=${config.keepMotionPhoto}")
    }
    if (targetFile == null) {
        targetFile = copy(config, context, uri, parsedInfo)
    }
    return targetFile
}

private suspend fun copy(config : Config, context: Context, uri: Uri, parsedInfo: UriParsedInfo) : File? {
    val copied = when (config.alwaysCopyMode) {
        "all"-> {
            uri.copyToCacheFile(context)
        }
        "onlyUri" -> {
            if (parsedInfo.isFile) {
                parsedInfo.file()
            } else {
                uri.copyToCacheFile(context)
            }
        }
        else -> { //none /null
            null
        }
    }

    return copied
}
