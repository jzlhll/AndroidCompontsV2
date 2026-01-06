package com.au.module_imagecompressed.compressor

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream


/**
 * Uri类型图片压缩器（保留原逻辑，作为第二个子类）
 */
internal class UriImageCompressor : BaseImageCompressor() {

    override fun provideInputStream(context: Context, source: Any): InputStream? {
        return context.contentResolver.openInputStream(source as Uri)
    }

    override suspend fun compress(context: Context, source: Any): String? {
        // 校验入参类型
        if (source !is Uri) return null
        //换成File来做
        if (source.scheme == "file")
            return FileImageCompressor().compress(context, source.toFile())

        return withContext(Dispatchers.IO) {
            runCatching {
                val (targetWidth, targetHeight, sampleSize) = decodeBefore(context, source)

                // 4. 解码并处理Bitmap
                val bitmap = decodeBitmap({
                    context.contentResolver.openInputStream(source)
                }, sampleSize)
                    ?.scaleTo(targetWidth, targetHeight)
                    ?.rotateByExif(File(source.path ?: "")) // 适配Uri转File的旋转逻辑
                    ?: throw IOException("Failed to decode or transform bitmap")

                saveFile(context, source, bitmap)
            }.getOrNull()
        }
    }

    override fun fileSize(context: Context, source: Any): Long {
        val uri = source as Uri
        return context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L
            } else 0L
        } ?: 0L
    }
}
