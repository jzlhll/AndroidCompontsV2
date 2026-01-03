package com.au.module_android.utilsmedia

import com.au.module_android.utilsmedia.ExtensionMimeUtil.Companion.isUriVideo
import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri

class VideoDurationHelper {
    /**
     * 使用系统方法获取 video/audio Url时长
     * @return 时长，毫秒
     */
    fun getDurationNormally(context: Context, uri: Uri): Long {
        var duration: Long = 0
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            duration = time!!.toLong()
        } catch (ex: java.lang.Exception) {
            ex.printStackTrace()
        } finally {
            retriever.release()
        }
        return duration
    }

    /**
     * 使用系统方法获取video/audio 时长
     * @return 时长，毫秒
     */
    fun getDurationNormally(path: String?): Long {
        var duration: Long = 0
        val retriever = MediaMetadataRetriever()
        try {
            if (path != null) {
                retriever.setDataSource(path)
            }
            val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            duration = time!!.toLong()
        } catch (ex: java.lang.Exception) {
            ex.printStackTrace()
        } finally {
            retriever.release()
        }
        return duration
    }

    fun getDurationNormally(path: String?, mimeType: String) : Long {
        return if (isUriVideo(mimeType)) {
            getDurationNormally(path)
        } else {
            0
        }
    }

    /**
     * 使用mediaPlayer准备的方式，来获取时长，据说更加精准。
     * @return 时长，毫秒
     */
    @Deprecated( "getDurationNormally(path)")
    fun getDurationComplexly(path: String?): Long {
        val mediaPlayer = MediaPlayer()
        try {
            mediaPlayer.setDataSource(path)
            mediaPlayer.prepare()
            val duration = mediaPlayer.duration
            mediaPlayer.release()
            return duration.toLong()
        } catch (e: java.lang.Exception) {
            mediaPlayer.release()
            e.printStackTrace()
        }
        return 0
    }
}