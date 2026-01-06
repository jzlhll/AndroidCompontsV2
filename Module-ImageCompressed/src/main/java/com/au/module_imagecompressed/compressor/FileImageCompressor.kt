package com.au.module_imagecompressed.compressor

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

/**
 * 本地File类型图片压缩器
 * 适配本地文件，queryFileSize 直接使用 File.length()
 */
internal class FileImageCompressor : BaseImageCompressor() {

    override fun provideInputStream(context: Context, source: Any): InputStream {
        return FileInputStream(source as File)
    }

    /**
     * 压缩本地File图片
     * @param context 上下文
     * @param source 本地图片文件
     * @return 压缩后的文件路径，失败返回null
     */
    override suspend fun compress(context: Context, source: Any): String? {
        // 校验入参类型
        if (source !is File) throw IllegalArgumentException("Invalid source type: must is File.")
        if (!source.exists() || !source.isFile) return null

        return withContext(Dispatchers.IO) {
            runCatching {
                val (targetWidth, targetHeight, sampleSize) = decodeBefore(context, source)

                // 4. 解码并处理Bitmap（缩放+旋转）
                val bitmap = decodeBitmap({ FileInputStream(source) }, sampleSize)
                    ?.scaleTo(targetWidth, targetHeight)
                    ?.rotateByExif(source)
                    ?: throw IOException("Failed to decode or transform bitmap")

                saveFile(context, source, bitmap)
            }.getOrNull()
        }
    }

    override fun fileSize(context: Context, source: Any): Long {
        val file = source as File
        return file.length()
    }

}