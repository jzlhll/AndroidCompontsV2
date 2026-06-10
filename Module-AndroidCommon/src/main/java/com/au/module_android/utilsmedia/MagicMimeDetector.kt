package com.au.module_android.utilsmedia

import android.webkit.MimeTypeMap
import com.au.module_android.utils.ignoreError
import java.io.File

/**
 * 通过文件头魔数探测 MIME 类型或扩展名（无后缀文件场景）。
 * 调用方已知媒体大类时，请使用对应的 detectXxxMimeType / detectXxxExtension，勿混用。
 */
object MagicMimeDetector {

    private const val HEADER_SIZE = 64

    private val HEIC_BRANDS = setOf(
        "heic", "heix", "heis", "heim", "hevc", "hevx", "hevs", "hevm", "heif",
    )
    private val HEIF_BRANDS = setOf(
        "mif1", "msf1", "miaf", "mif2", "miab", "miac", "hif1",
    )
    private val AVIF_BRANDS = setOf("avif", "avis")

    private val VIDEO_FTYP_BRANDS = setOf(
        "isom", "iso2", "iso3", "iso4", "iso5", "iso6", "iso7", "iso8", "iso9",
        "mp41", "mp42", "mp71", "mmp4", "avc1", "avc3", "hvc1", "hev1", "hvt1",
        "av01", "dby1", "M4V ", "f4v ", "dash", "cmfc", "ndsc", "msdh", "msdv",
        "3gp5", "3g2c", "3g2b", "3gs7",
    )

    private val AUDIO_FTYP_BRANDS = setOf("M4A ", "M4B ")

    /** 仅识别图片魔数 */
    fun detectImageMimeType(file: File): String? {
        return readHeader(file)?.let { (buffer, bytesRead) ->
            detectImageFromHeader(buffer, bytesRead)
        }
    }

    /** 仅识别音频魔数 */
    fun detectAudioMimeType(file: File): String? {
        return readHeader(file)?.let { (buffer, bytesRead) ->
            detectAudioFromHeader(buffer, bytesRead)
        }
    }

    /** 仅识别视频魔数 */
    fun detectVideoMimeType(file: File): String? {
        return readHeader(file)?.let { (buffer, bytesRead) ->
            detectVideoFromHeader(buffer, bytesRead)
        }
    }

    /** 仅识别图片扩展名 */
    fun detectImageExtension(file: File): String? {
        return mimeToExtension(detectImageMimeType(file))
    }

    /** 仅识别音频扩展名 */
    fun detectAudioExtension(file: File): String? {
        return mimeToExtension(detectAudioMimeType(file))
    }

    /** 仅识别视频扩展名 */
    fun detectVideoExtension(file: File): String? {
        return mimeToExtension(detectVideoMimeType(file))
    }

    private fun mimeToExtension(mime: String?): String? {
        if (mime == null) {
            return null
        }
        val fromMap = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
        if (!fromMap.isNullOrEmpty()) {
            return fromMap
        }
        return EXTENSION_FALLBACK[mime]
    }

    private val EXTENSION_FALLBACK = mapOf(
        "image/heic" to "heic",
        "image/heif" to "heif",
        "image/avif" to "avif",
        "image/jxl" to "jxl",
        "image/jp2" to "jp2",
        "image/x-icon" to "ico",
        "image/vnd.adobe.photoshop" to "psd",
        "image/x-canon-cr2" to "cr2",
        "image/svg+xml" to "svg",
        "audio/mp4" to "m4a",
        "audio/midi" to "mid",
        "video/mp2t" to "ts",
        "video/x-matroska" to "mkv",
        "video/x-msvideo" to "avi",
        "video/x-flv" to "flv",
        "video/x-ms-wmv" to "wmv",
        "video/avc" to "h264",
        "video/hevc" to "hevc",
        "video/vnd.rn-realvideo" to "rmvb",
        "video/ogg" to "ogv",
        "video/mj2" to "mj2",
    )

    private fun readHeader(file: File): Pair<ByteArray, Int>? {
        if (!file.isFile || file.length() < 4) {
            return null
        }
        return ignoreError {
            file.inputStream().use { input ->
                val buffer = ByteArray(HEADER_SIZE)
                val bytesRead = input.read(buffer)
                if (bytesRead < 4) {
                    return@ignoreError null
                }
                buffer to bytesRead
            }
        }
    }

    private fun detectImageFromHeader(buffer: ByteArray, bytesRead: Int): String? {
        if (matchAt(buffer, 0, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg"
        }
        if (matchAt(buffer, 0, 0x89, 0x50, 0x4E, 0x47)) {
            return "image/png"
        }
        if (matchAt(buffer, 0, 0x42, 0x4D)) {
            return "image/bmp"
        }
        detectImageFromFtyp(buffer, bytesRead)?.let { return it }
        if (bytesRead >= 12 && matchAt(buffer, 0, 0x52, 0x49, 0x46, 0x46)
            && matchAt(buffer, 8, 0x57, 0x45, 0x42, 0x50)
        ) {
            return "image/webp"
        }
        if (matchAt(buffer, 0, 0x47, 0x49, 0x46, 0x38)) {
            return "image/gif"
        }
        if (matchAt(buffer, 0, 0xFF, 0x0A)) {
            return "image/jxl"
        }
        if (matchAt(buffer, 0, 0xFF, 0x4F, 0xFF, 0x51)) {
            return "image/jp2"
        }
        if (matchAt(buffer, 0, 0x00, 0x00, 0x01, 0x00)) {
            return "image/x-icon"
        }
        if (matchAt(buffer, 0, 0x38, 0x42, 0x50, 0x53)) {
            return "image/vnd.adobe.photoshop"
        }
        if (bytesRead >= 10
            && matchAt(buffer, 0, 0x49, 0x49, 0x2A, 0x00)
            && matchAt(buffer, 8, 0x43, 0x52)
        ) {
            return "image/x-canon-cr2"
        }
        if (matchAt(buffer, 0, 0x49, 0x49, 0x2A, 0x00) || matchAt(buffer, 0, 0x4D, 0x4D, 0x00, 0x2A)) {
            return "image/tiff"
        }
        if (bytesRead >= 12 && matchAt(buffer, 4, 0x4A, 0x58, 0x4C, 0x20)) {
            return "image/jxl"
        }
        if (bytesRead >= 12 && matchAt(buffer, 4, 0x6A, 0x50, 0x20, 0x20)) {
            return "image/jp2"
        }
        if (matchAt(buffer, 0, 0x3C, 0x73, 0x76, 0x67)
            || matchAt(buffer, 0, 0x3C, 0x3F, 0x78, 0x6D)
        ) {
            return "image/svg+xml"
        }
        return null
    }

    private fun detectAudioFromHeader(buffer: ByteArray, bytesRead: Int): String? {
        if (matchAt(buffer, 0, 0x49, 0x44, 0x33)) {
            return "audio/mpeg"
        }
        if (buffer[0].toInt() and 0xFF == 0xFF && (buffer[1].toInt() and 0xE0) == 0xE0) {
            val b1 = buffer[1].toInt() and 0xFF
            if (b1 == 0xF1 || b1 == 0xF9) {
                return "audio/aac"
            }
            return "audio/mpeg"
        }
        if (matchAt(buffer, 0, 0x4F, 0x67, 0x67, 0x53)) {
            return "audio/ogg"
        }
        if (bytesRead >= 5 && matchAt(buffer, 0, 0x23, 0x21, 0x41, 0x4D, 0x52)) {
            return "audio/amr"
        }
        if (bytesRead >= 12 && matchAt(buffer, 0, 0x52, 0x49, 0x46, 0x46)
            && matchAt(buffer, 8, 0x57, 0x41, 0x56, 0x45)
        ) {
            return "audio/wav"
        }
        if (matchAt(buffer, 0, 0x66, 0x4C, 0x61, 0x43)) {
            return "audio/flac"
        }
        if (matchAt(buffer, 0, 0x4D, 0x54, 0x68, 0x64)) {
            return "audio/midi"
        }
        detectAudioFromFtyp(buffer, bytesRead)?.let { return it }
        return null
    }

    private fun detectVideoFromHeader(buffer: ByteArray, bytesRead: Int): String? {
        // MP4 / MOV / M4V / 3GP（ISO BMFF ftyp）
        detectVideoFromFtyp(buffer, bytesRead)?.let { return it }
        // MOV：mdat/moov 在前的旧式 QuickTime
        if (matchAt(buffer, 0, 0x6D, 0x6F, 0x6F, 0x76)) {
            return "video/quicktime"
        }
        if (matchAt(buffer, 0, 0x66, 0x72, 0x65, 0x65)) {
            return "video/quicktime"
        }
        // MPEG-TS（.ts / .m2ts）
        if (isMpegTransportStream(buffer)) {
            return "video/mp2t"
        }
        // WebM / MKV（EBML）
        detectEbmlVideo(buffer, bytesRead)?.let { return it }
        // AVI（RIFF）
        if (bytesRead >= 12 && matchAt(buffer, 0, 0x52, 0x49, 0x46, 0x46)) {
            when {
                matchAt(buffer, 8, 0x41, 0x56, 0x49, 0x20) -> return "video/x-msvideo"
                matchAt(buffer, 8, 0x57, 0x4D, 0x56, 0x32) -> return "video/x-ms-wmv"
                matchAt(buffer, 8, 0x57, 0x4D, 0x56, 0x33) -> return "video/x-ms-wmv"
            }
        }
        // FLV / F4V（Adobe）
        if (matchAt(buffer, 0, 0x46, 0x4C, 0x56, 0x01) || matchAt(buffer, 0, 0x46, 0x4C, 0x56, 0x05)) {
            return "video/x-flv"
        }
        // ASF / WMV
        if (matchAt(buffer, 0, 0x30, 0x26, 0xB2, 0x75)) {
            return "video/x-ms-wmv"
        }
        // MPEG-PS / VOB
        if (matchAt(buffer, 0, 0x00, 0x00, 0x01, 0xBA)
            || matchAt(buffer, 0, 0x00, 0x00, 0x01, 0xB3)
        ) {
            return "video/mpeg"
        }
        // H.264 / H.265 裸流
        if (isH264AnnexB(buffer, bytesRead)) {
            return "video/avc"
        }
        if (isHevcAnnexB(buffer, bytesRead)) {
            return "video/hevc"
        }
        // RealMedia（RM / RMVB）
        if (bytesRead >= 4 && matchAt(buffer, 0, 0x2E, 0x52, 0x61, 0x72)) {
            return "video/vnd.rn-realvideo"
        }
        // Ogg 容器视频（Theora 等）
        if (matchAt(buffer, 0, 0x4F, 0x67, 0x67, 0x53)) {
            return "video/ogg"
        }
        return null
    }

    private fun isMpegTransportStream(buffer: ByteArray): Boolean {
        return (buffer[0].toInt() and 0xFF) == 0x47
            && (buffer[1].toInt() and 0x40) != 0
    }

    // EBML header 中 docType 字段通常在前 40 字节内，HEADER_SIZE=64 足够覆盖
    private fun detectEbmlVideo(buffer: ByteArray, bytesRead: Int): String? {
        if (!matchAt(buffer, 0, 0x1A, 0x45, 0xDF, 0xA3)) {
            return null
        }
        val head = String(buffer, 0, bytesRead, Charsets.US_ASCII)
        return when {
            head.contains("webm", ignoreCase = true) -> "video/webm"
            head.contains("matroska", ignoreCase = true) -> "video/x-matroska"
            else -> "video/x-matroska"
        }
    }

    private fun annexBStartCodeLen(buffer: ByteArray, bytesRead: Int): Int {
        return when {
            bytesRead >= 4 && matchAt(buffer, 0, 0x00, 0x00, 0x00, 0x01) -> 4
            matchAt(buffer, 0, 0x00, 0x00, 0x01) -> 3
            else -> 0
        }
    }

    private fun isH264AnnexB(buffer: ByteArray, bytesRead: Int): Boolean {
        val startCodeLen = annexBStartCodeLen(buffer, bytesRead)
        if (startCodeLen == 0 || bytesRead <= startCodeLen) {
            return false
        }
        val nalType = buffer[startCodeLen].toInt() and 0x1F
        return nalType == 1 || nalType == 5 || nalType == 7 || nalType == 8
    }

    private fun isHevcAnnexB(buffer: ByteArray, bytesRead: Int): Boolean {
        val startCodeLen = annexBStartCodeLen(buffer, bytesRead)
        if (startCodeLen == 0 || bytesRead <= startCodeLen) {
            return false
        }
        val nalType = (buffer[startCodeLen].toInt() and 0xFF) shr 1
        return nalType in 32..34 || nalType == 19 || nalType == 20
    }

    private fun detectImageFromFtyp(buffer: ByteArray, bytesRead: Int): String? {
        if (bytesRead >= 12 && matchAt(buffer, 4, 0x66, 0x74, 0x79, 0x70)) {
            scanFtypBrands(buffer, bytesRead, brandOffset = 8, ::imageMimeFromBrand)?.let { return it }
        }
        if (bytesRead >= 8 && matchAt(buffer, 0, 0x66, 0x74, 0x79, 0x70)) {
            scanFtypBrands(buffer, bytesRead, brandOffset = 4, ::imageMimeFromBrand)?.let { return it }
        }
        return null
    }

    private fun detectAudioFromFtyp(buffer: ByteArray, bytesRead: Int): String? {
        if (bytesRead >= 12 && matchAt(buffer, 4, 0x66, 0x74, 0x79, 0x70)) {
            scanFtypBrands(buffer, bytesRead, brandOffset = 8, ::audioMimeFromBrand)?.let { return it }
        }
        if (bytesRead >= 8 && matchAt(buffer, 0, 0x66, 0x74, 0x79, 0x70)) {
            scanFtypBrands(buffer, bytesRead, brandOffset = 4, ::audioMimeFromBrand)?.let { return it }
        }
        return null
    }

    private fun detectVideoFromFtyp(buffer: ByteArray, bytesRead: Int): String? {
        if (bytesRead >= 12 && matchAt(buffer, 4, 0x66, 0x74, 0x79, 0x70)) {
            return mimeFromFtypVideo(buffer, bytesRead, brandOffset = 8)
        }
        if (bytesRead >= 8 && matchAt(buffer, 0, 0x66, 0x74, 0x79, 0x70)) {
            return mimeFromFtypVideo(buffer, bytesRead, brandOffset = 4)
        }
        return null
    }

    private fun scanFtypBrands(
        buffer: ByteArray,
        bytesRead: Int,
        brandOffset: Int,
        mapBrand: (String) -> String?,
    ): String? {
        if (bytesRead < brandOffset + 4) {
            return null
        }
        var off = brandOffset
        while (off + 4 <= bytesRead) {
            mapBrand(brandAt(buffer, off))?.let { return it }
            off += 4
            if (off > brandOffset + 20) {
                break
            }
        }
        return mapBrand(brandAt(buffer, brandOffset))
    }

    private fun mimeFromFtypVideo(buffer: ByteArray, bytesRead: Int, brandOffset: Int): String {
        if (bytesRead < brandOffset + 4) {
            return "video/mp4"
        }
        var off = brandOffset
        while (off + 4 <= bytesRead) {
            videoMimeFromBrand(brandAt(buffer, off))?.let { return it }
            off += 4
            if (off > brandOffset + 20) {
                break
            }
        }
        return videoMimeFromBrand(brandAt(buffer, brandOffset)) ?: "video/mp4"
    }

    private fun imageMimeFromBrand(brand: String): String? {
        return when {
            brand in HEIC_BRANDS -> "image/heic"
            brand in HEIF_BRANDS -> "image/heif"
            brand in AVIF_BRANDS -> "image/avif"
            else -> null
        }
    }

    private fun audioMimeFromBrand(brand: String): String? {
        return if (brand in AUDIO_FTYP_BRANDS) {
            "audio/mp4"
        } else {
            null
        }
    }

    private fun videoMimeFromBrand(brand: String): String? {
        return when (brand) {
            "3gp4", "3g2a", "3ge6", "3gg6", "3gp5", "3g2c", "3g2b", "3gs7" -> "video/3gpp"
            "qt  " -> "video/quicktime"
            "f4v " -> "video/x-flv"
            "mj2 ", "mjp2" -> "video/mj2"
            in VIDEO_FTYP_BRANDS -> "video/mp4"
            else -> null
        }
    }

    private fun brandAt(buffer: ByteArray, offset: Int): String {
        return String(buffer, offset, 4, Charsets.US_ASCII)
    }

    private fun matchAt(buffer: ByteArray, offset: Int, vararg pattern: Int): Boolean {
        if (offset + pattern.size > buffer.size) {
            return false
        }
        for (i in pattern.indices) {
            if ((buffer[offset + i].toInt() and 0xFF) != pattern[i]) {
                return false
            }
        }
        return true
    }
}
