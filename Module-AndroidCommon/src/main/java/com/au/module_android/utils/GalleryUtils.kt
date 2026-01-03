package com.au.module_android.utils

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.io.IOException

class GalleryUtils private constructor(){
    companion object {

        private const val VIDEO_BASE_URI = "content://media/external/video/media"

        /***
         * @param srcPath
         * @param context
         */
        fun insertImage(srcPath: String, destDir: String?, destName: String?, context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues()
                values.put(MediaStore.Images.Media.DISPLAY_NAME, destName)
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                val contentResolver = context.contentResolver
                val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val item = contentResolver.insert(collection, values)
                writeFile(srcPath, values, contentResolver, item)
                contentResolver.update(item!!, values, null, null)
            } else {
                copyFile(File(srcPath), destDir, destName)
            }
        }

        /***
         *
         * @param videoPath
         * @param context
         */
        fun insertVideo(videoPath: String, context: Context) {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(videoPath)
            val nVideoWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!.toInt()
            val nVideoHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!.toInt()
            val duration = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toInt()
            val dateTaken = System.currentTimeMillis()
            val file = File(videoPath)
            val title = file.name
            val filename = file.name
            val mime = "video/mp4"
            val mCurrentVideoValues = ContentValues(9)
            mCurrentVideoValues.put(MediaStore.Video.Media.TITLE, title)
            mCurrentVideoValues.put(MediaStore.Video.Media.DISPLAY_NAME, filename)
            mCurrentVideoValues.put(MediaStore.Video.Media.DATE_TAKEN, dateTaken)
            mCurrentVideoValues.put(MediaStore.MediaColumns.DATE_MODIFIED, dateTaken / 1000)
            mCurrentVideoValues.put(MediaStore.Video.Media.MIME_TYPE, mime)
            mCurrentVideoValues.put(MediaStore.Video.Media.DATA, videoPath)
            mCurrentVideoValues.put(MediaStore.Video.Media.WIDTH, nVideoWidth)
            mCurrentVideoValues.put(MediaStore.Video.Media.HEIGHT, nVideoHeight)
            mCurrentVideoValues.put(MediaStore.Video.Media.RESOLUTION, Integer.toString(nVideoWidth) + "x" + Integer.toString(nVideoHeight))
            mCurrentVideoValues.put(MediaStore.Video.Media.SIZE, File(videoPath).length())
            mCurrentVideoValues.put(MediaStore.Video.Media.DURATION, duration)
            val contentResolver = context.contentResolver
            val videoTable = Uri.parse(VIDEO_BASE_URI)
            val uri = contentResolver.insert(videoTable, mCurrentVideoValues)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                writeFile(videoPath, mCurrentVideoValues, contentResolver, uri)
            }
        }

        @RequiresApi(api = Build.VERSION_CODES.Q)
        private fun writeFile(imagePath: String, values: ContentValues, contentResolver: ContentResolver, item: Uri?) {
            try {
                contentResolver.openOutputStream(item!!, "rw").use { rw ->
                    // Write data into the pending image.
                    val sink = rw!!.sink()
                    val buffer = File(imagePath).source().buffer()
                    buffer.readAll(sink)
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    contentResolver.update(item, values, null, null)
                    File(imagePath).delete()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val query = contentResolver.query(item, null, null, null)
                        if (query != null) {
                            val count = query.count
                            Log.e("writeFile", "writeFile result :$count")
                            query.close()
                        }
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}