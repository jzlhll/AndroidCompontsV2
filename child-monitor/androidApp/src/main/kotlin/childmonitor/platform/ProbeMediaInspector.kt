package childmonitor.platform

import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File

data class ProbeMedia(val width: Int, val height: Int, val firstPtsUs: Long, val lastPtsUs: Long)

/** 检查临时 MP4 的实际轨道与样本时间，不将录像回调时间当成首帧时间。 */
object ProbeMediaInspector {
    fun read(file: File): ProbeMedia {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            var videoTrack = -1
            var width = 0
            var height = 0
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                check(!mime.startsWith("audio/")) { "Unexpected audio track" }
                if (mime.startsWith("video/")) {
                    check(videoTrack == -1) { "Unexpected extra video track" }
                    videoTrack = index
                    width = format.getInteger(MediaFormat.KEY_WIDTH)
                    height = format.getInteger(MediaFormat.KEY_HEIGHT)
                }
            }
            check(videoTrack >= 0 && isSupportedSize(width, height)) { "Unsupported video dimensions" }
            extractor.selectTrack(videoTrack)
            val first = extractor.sampleTime
            check(first >= 0) { "Missing video samples" }
            var last = first
            while (extractor.advance()) {
                if (extractor.sampleTime > last) last = extractor.sampleTime
            }
            check(last - first >= 3_000_000L) { "Insufficient video samples" }
            return ProbeMedia(width, height, first, last)
        } finally {
            extractor.release()
        }
    }

    fun isSupportedSize(width: Int, height: Int): Boolean {
        val shortEdge = if (width < height) width else height
        val longEdge = if (width > height) width else height
        return shortEdge in 1..480 && longEdge in 1..854
    }
}
