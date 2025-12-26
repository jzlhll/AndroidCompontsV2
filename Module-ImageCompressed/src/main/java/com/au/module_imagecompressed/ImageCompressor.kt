package com.au.module_imagecompressed

import android.content.Context
import android.graphics.*
import android.graphics.Bitmap.createBitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * todo 看看是否支持heic
 * @author muhammet_konukcu
 * createdAt: 08.01.2025
 * The photo quality is adjusted according to the file size.
 * You can change it according to your wishes. chooseQuality()
 */
class ImageCompressor {

    private val maxHeight = 1920f
    private val maxWidth = 1080f
    private val tempBufferSize = 16 * 1024

    suspend fun compress(context: Context, uriString: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val sourceUri = uriString.toUri()
                val originalOptions = decodeBounds(context, sourceUri)

                val (targetWidth, targetHeight) = calculateTargetDimensions(
                    originalOptions.outWidth,
                    originalOptions.outHeight
                )

                val sampleSize = calculateSampleSize(
                    originalOptions,
                    targetWidth,
                    targetHeight
                )

                val bitmap = decodeBitmap(context, sourceUri, sampleSize)
                    ?.scaleTo(targetWidth, targetHeight)
                    ?.rotateByExif(context, sourceUri)
                    ?: throw IOException("Failed to decode or transform bitmap")

                val quality = chooseQuality(context, sourceUri)
                val outputFile = createOutputFile(context)

                FileOutputStream(outputFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                }

                outputFile.absolutePath
            }.getOrNull()
        }

    private fun decodeBounds(context: Context, uri: Uri): BitmapFactory.Options {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
        return options
    }

    private fun calculateTargetDimensions(
        width: Int,
        height: Int
    ): Pair<Int, Int> {
        var w = width.toFloat()
        var h = height.toFloat()
        val imgRatio = w / h
        val maxRatio = maxWidth / maxHeight

        if (h > maxHeight || w > maxWidth) {
            if (imgRatio < maxRatio) {
                val scale = maxHeight / h
                w = scale * w
                h = maxHeight
            } else if (imgRatio > maxRatio) {
                val scale = maxWidth / w
                h = scale * h
                w = maxWidth
            } else {
                w = maxWidth
                h = maxHeight
            }
        }
        return w.roundToInt() to h.roundToInt()
    }

    private fun calculateSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSample = 1
        val (origW, origH) = options.outWidth to options.outHeight

        if (origH > reqHeight || origW > reqWidth) {
            val heightRatio = (origH.toFloat() / reqHeight).roundToInt()
            val widthRatio = (origW.toFloat() / reqWidth).roundToInt()
            inSample = minOf(heightRatio, widthRatio)
        }

        val totalPixels = origW * origH.toFloat()
        val cap = reqWidth * reqHeight * 2f
        while (totalPixels / (inSample * inSample) > cap) {
            inSample++
        }
        return inSample
    }

    private fun decodeBitmap(
        context: Context,
        uri: Uri,
        sampleSize: Int
    ): Bitmap? {
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inJustDecodeBounds = false
            inPurgeable = true
            inInputShareable = true
            inTempStorage = ByteArray(tempBufferSize)
        }
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, opts)
        }
    }

    private fun Bitmap.scaleTo(width: Int, height: Int): Bitmap {
        val scaled = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        val ratioX = width / this.width.toFloat()
        val ratioY = height / this.height.toFloat()
        val midX = width / 2f
        val midY = height / 2f

        val matrix = Matrix().apply { setScale(ratioX, ratioY, midX, midY) }
        Canvas(scaled).apply {
            setMatrix(matrix)
            drawBitmap(
                this@scaleTo,
                midX - this@scaleTo.width / 2f,
                midY - this@scaleTo.height / 2f,
                Paint(Paint.FILTER_BITMAP_FLAG)
            )
        }
        return scaled
    }

    private fun Bitmap.rotateByExif(context: Context, uri: Uri): Bitmap? =
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED
                )

                val rotateMatrix = Matrix().apply {
                    when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                        ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                        ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                        else -> {}
                    }
                }
                createBitmap(this, 0, 0, width, height, rotateMatrix, true)
            }
        }.getOrDefault(this)

    private fun chooseQuality(context: Context, uri: Uri): Int {
        val size = queryFileSize(context, uri)
        return when {
            size > 5_000_000 -> 40
            size > 2_000_000 -> 50
            size > 1_000_000 -> 60
            size > 500_000 -> 70
            size > 250_000 -> 80
            else -> 90
        }
    }

    private fun queryFileSize(context: Context, uri: Uri): Long {
        return when (uri.scheme) {
            "content" -> context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0) cursor.getLong(sizeIndex) else -1L
                } else -1L
            } ?: -1L

            "file" -> File(uri.path ?: return -1L).takeIf { it.exists() }?.length() ?: -1L
            else -> -1L
        }
    }

    private fun createOutputFile(context: Context): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val randomSuffix = Random.nextInt(100_000, 1_000_000)
        val fileName = "IMG_${timestamp}_${randomSuffix}.jpg"
        val dir = File(context.getExternalFilesDir(null), "Pictures").apply {
            if (!exists()) mkdirs()
        }
        return File(dir, fileName)
    }
}