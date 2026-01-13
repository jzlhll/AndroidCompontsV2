package com.au.module_imagecompressed.compressor

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.au.module_android.log.logdNoFile
import com.au.module_android.utils.ignoreError
import com.au.module_android.utilsfile.SimpleFilesLruCache
import com.au.module_android.utilsmedia.UriParsedInfo
import com.au.module_android.utilsmedia.myParse
import com.au.module_imagecompressed.compressor.BestImageCompressor.Config
import com.au.module_imagecompressed.copyToCacheFile
import java.io.File
import java.io.FileInputStream
import java.util.UUID

/**
 * 公开使用的函数
 */
suspend fun useCompress(context: Context, uri: Uri, config: Config = Config()) : File? {
    val parsedInfo = uri.myParse(context)
    val magicCode = UUID.randomUUID().toString().substring(0, 8) + " Compress:$uri"

    if (!parsedInfo.isUriImage()) {
        "".logdNoFile{ "$magicCode Don't pass not image uri to Compressor!" }
        return null
    }

    "".logdNoFile { "$magicCode parsed info extension ${parsedInfo.extension}" }

    //1. 判断忽略大小和文件类型
    val beIgnore = parsedInfo.fileLength < config.ignoreSizeInKB * 1024
            || config.ignoreFileTypes.contains(parsedInfo.extension.lowercase())

    var targetFile: File? = null
    if (!beIgnore) {
        //2. 开始压缩
        val isFile = parsedInfo.isFile
        val compressor = createBestImageCompressor(isFile, parsedInfo, config, context, uri)
        val compressed = ignoreError { compressor.compress() }
        if (compressed != null) {
            val compressedSize = compressed.length()
            if (compressedSize <= parsedInfo.fileLength) {
                //2.2 没有变大，返回压缩文件
                CompressCacheConstManager.afterFileOperator(compressed, SimpleFilesLruCache.FileOperateType.SAVE)
                targetFile = compressed
            } else {
                //2.3 压缩变大了
                ignoreError { compressed.delete() }
            }
        } else {
            "".logdNoFile { "$magicCode compress fail." }
        }
    } else {
        "".logdNoFile { "$magicCode ignored." }
    }

    if (beIgnore || targetFile == null) {
        targetFile = copy(config, context, uri, parsedInfo)
        "".logdNoFile { "$magicCode copied: $targetFile" }
    }
    return targetFile
}

private fun createBestImageCompressor(
    isFile: Boolean,
    parsedInfo: UriParsedInfo,
    config: Config?,
    context: Context,
    uri: Uri
): BestImageCompressor {
    val compressor = if (isFile) {
        val source = parsedInfo.file()!!
        BestImageCompressor(
            config = config ?: Config(),
            provideInputStream = {
                FileInputStream(source)
            },
            provideFileSize = {
                parsedInfo.fileLength
            },
            provideImageDecodeSource = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                    ImageDecoder.createSource(source)
                else null
            }
        )
    } else {
        BestImageCompressor(
            config = config ?: Config(),
            provideInputStream = {
                context.contentResolver.openInputStream(uri)
            },
            provideFileSize = {
                parsedInfo.fileLength
            },
            provideImageDecodeSource = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                    ImageDecoder.createSource(context.contentResolver, uri)
                else null
            }
        )
    }
    return compressor
}

private suspend fun copy(config : Config, context: Context, uri: Uri, parsedInfo: UriParsedInfo) : File? {
    val copied = when (config.alwaysCopyMode) {
        "all"-> {
            uri.copyToCacheFile(context)
        }
        "onlyUri" -> {
            if (parsedInfo.isFile) {
                uri.copyToCacheFile(context)
            } else {
                null
            }
        }
        else -> { //none /null
            null
        }
    }

    return copied
}