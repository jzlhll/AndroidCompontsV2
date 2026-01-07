package com.au.module_imagecompressed

import android.content.Context
import android.net.Uri
import com.au.module_imagecompressed.compressor.systemCompressFile
import com.au.module_imagecompressed.compressor.systemCompressUriOrCopy
import java.io.File

interface ICompressEngine {
    /**
     * 从远程的Uri中进行图片压缩。
     * 你不需要判断Uri是不是本地file。它一定是远程
     */
    suspend fun compress(context: Context, uri: Uri) : File

    /**
     * 从本地file中进行图片压缩。
     */
    suspend fun compress(context: Context, file: File) : File?
}

fun defaultCompressEngine(ignoreSizeKb:Int = 250) : ICompressEngine {
    return object : ICompressEngine {
        override suspend fun compress(context: Context, uri: Uri): File {
            return systemCompressUriOrCopy(context, uri)
        }

        override suspend fun compress(context: Context, file: File): File? {
            if (file.length() < ignoreSizeKb * 1024) {
                return file
            }
            return systemCompressFile(file)
        }
    }
}