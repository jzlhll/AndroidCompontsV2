package com.au.module_imagecompressed.compressor

import android.content.Context
import android.net.Uri
import java.io.File

suspend fun compress(context: Context, source: File): String? {
    return FileImageCompressor().compress(context, source)
}

suspend fun compress(context: Context, uri: Uri) : String? {
    return UriImageCompressor().compress(context, uri)
}