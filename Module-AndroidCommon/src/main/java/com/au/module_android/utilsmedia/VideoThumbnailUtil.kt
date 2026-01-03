package com.au.module_android.utilsmedia

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import java.io.File
import java.io.IOException
import java.io.InputStream

fun getThumbnail(cr: ContentResolver, file: File, width:Int, height:Int): Bitmap? {
    return getThumbnail(cr, Uri.fromFile(file), width, height)
}

fun getThumbnail(cr:ContentResolver, contentUri: Uri, width:Int, height:Int): Bitmap? {
    try {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cr.loadThumbnail(contentUri, Size(width, height), null)
        } else {
            val path = contentUri.path
            if (path == null) {
                null
            } else {
                ThumbnailUtils.createVideoThumbnail(path, MediaStore.Images.Thumbnails.MINI_KIND)
            }
        }
    } catch (e:Exception) {
        e.printStackTrace()
    }
    return null
}

@Throws(IOException::class)
fun createThumbnailFromImageInputStream(inputStream: InputStream?, width: Int, height: Int): Bitmap? {
    val options = BitmapFactory.Options()
    options.inJustDecodeBounds = true
    // Calculate inSampleSize
    options.inSampleSize = calculateInSampleSize(options, width, height)

    // Decode bitmap with inSampleSize set
    options.inJustDecodeBounds = false
    return BitmapFactory.decodeStream(inputStream, null, options)
}

private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    // Raw height and width of image
    val height = options.outHeight
    val width = options.outWidth
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2

        // Calculate the largest inSampleSize value that is a power of 2 and keeps both
        // height and width larger than the requested height and width.
        while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
            inSampleSize *= 2
        }
    }

    return inSampleSize
}