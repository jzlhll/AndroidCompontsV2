package com.au.module_imagecompressed.compressor

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.au.module_android.utils.ignoreError
import com.au.module_android.utilsfile.SimpleFilesLruCache
import com.au.module_android.utilsmedia.length
import com.au.module_imagecompressed.copyToCacheFile
import java.io.File
import java.io.FileInputStream

suspend fun systemCompressFile(source: File) : File?{
    val f = ImageCompressor(
        provideInputStream = {
            FileInputStream(source)
        },
        provideFileSize = {
            source.length()
        },
        provideImageDecodeSource = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                ImageDecoder.createSource(source)
            else null
        }
    ).compress()
    return if (f != null) {
        CompressCacheConstManager.mgr.afterFileOperator(f, SimpleFilesLruCache.FileOperateType.SAVE)
        f
    } else {
        null
    }
}

suspend fun systemCompressUri(context: Context, uri: Uri, targetSize: Long?=null) : File? {
    val f = ImageCompressor(
        config = ImageCompressor.Config().also {
            it.targetSize = targetSize
        },
        provideInputStream = {
            context.contentResolver.openInputStream(uri)
        },
        provideFileSize = {
            uri.length(context.contentResolver)
        },
        provideImageDecodeSource = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                ImageDecoder.createSource(context.contentResolver, uri)
            else null
        }
    ).compress()
    return if (f != null) {
        CompressCacheConstManager.mgr.afterFileOperator(f, SimpleFilesLruCache.FileOperateType.SAVE)
        f
    } else {
        null
    }
}

/**
 * 压缩不成就拷贝
 */
suspend fun systemCompressUriOrCopy(context: Context, uri: Uri, targetSize:Long?=null) : File {
    val f = ignoreError { systemCompressUri(context, uri, targetSize) }
    return f ?: uri.copyToCacheFile(context)
}