package com.au.module_imagecompressed

import android.content.Context
import android.net.Uri
import com.au.module_imagecompressed.compressor.BestImageCompressor
import com.au.module_imagecompressed.compressor.useCompress
import java.io.File

interface ICompressEngine {
    /**
     * 从远程的Uri中进行图片压缩。如果是file，则使用file.toUri()传进来
     */
    suspend fun compress(context: Context, uri: Uri) : File?
}

fun defaultCompressEngine(config: BestImageCompressor.Config? = null) : ICompressEngine {
    return object : ICompressEngine {
        override suspend fun compress(context: Context, uri: Uri): File? {
            return useCompress(context, uri, config ?: BestImageCompressor.Config())
        }
    }
}