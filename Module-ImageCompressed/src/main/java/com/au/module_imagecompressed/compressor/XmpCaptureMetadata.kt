package com.au.module_imagecompressed.compressor

import android.util.Xml
import androidx.exifinterface.media.ExifInterface
import com.au.module_android.log.logdNoFile
import kotlinx.coroutines.CancellationException
import org.xmlpull.v1.XmlPullParser
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private const val EXIF_NAMESPACE = "http://ns.adobe.com/exif/1.0/"
private const val EXIF_EX_NAMESPACE = "http://cipa.jp/exif/1.0/"
private const val EXIF_AUX_NAMESPACE = "http://ns.adobe.com/exif/1.0/aux/"
private const val PHOTOSHOP_NAMESPACE = "http://ns.adobe.com/photoshop/1.0/"
private val CAPTURE_PROPERTIES = setOf(
    EXIF_NAMESPACE to "DateTimeOriginal",
    PHOTOSHOP_NAMESPACE to "DateCreated",
    EXIF_EX_NAMESPACE to "LensModel",
    EXIF_NAMESPACE to "LensModel",
    EXIF_AUX_NAMESPACE to "Lens",
)
private val EXIF_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.ROOT)

/** 尽力补齐缺失的拍摄时间和镜头型号，返回新增标签供保存失败时撤回。 */
internal fun ExifInterface.copyMissingXmpCaptureMetadataTo(outputExif: ExifInterface): Set<String> {
    val addedTags = mutableSetOf<String>()
    try {
        val needsLens = outputExif.getAttribute(ExifInterface.TAG_LENS_MODEL).isNullOrBlank()
        val needsTime = outputExif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL).isNullOrBlank()
        if (!needsLens && !needsTime) return addedTags
        val xmp = getAttributeBytes(ExifInterface.TAG_XMP) ?: return addedTags
        val properties = readXmpCaptureProperties(xmp)
        val lens = properties[EXIF_EX_NAMESPACE to "LensModel"]
            ?: properties[EXIF_NAMESPACE to "LensModel"]
            ?: properties[EXIF_AUX_NAMESPACE to "Lens"]
        if (needsLens && lens != null) {
            addedTags.add(ExifInterface.TAG_LENS_MODEL)
            outputExif.setAttribute(ExifInterface.TAG_LENS_MODEL, lens)
        }
        if (needsTime) {
            // DateCreated 表示原始拍摄时间，不使用数字化创建时间 CreateDate 替代。
            val captureTimes = listOfNotNull(
                properties[EXIF_NAMESPACE to "DateTimeOriginal"],
                properties[PHOTOSHOP_NAMESPACE to "DateCreated"],
            )
            for (value in captureTimes) {
                val dateTime = try {
                    LocalDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME).format(EXIF_DATE_TIME_FORMAT)
                } catch (_: DateTimeParseException) {
                    continue
                }
                addedTags.add(ExifInterface.TAG_DATETIME_ORIGINAL)
                outputExif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateTime)
                break
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logdNoFile("ImageProcess") { "copy XMP capture metadata skipped: ${e.message}" }
    }
    return addedTags
}

// 同时兼容 RDF 属性和独立元素，按命名空间识别目标字段。
private fun readXmpCaptureProperties(xmp: ByteArray): Map<Pair<String, String>, String> {
    val parser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        setInput(xmp.inputStream(), null)
    }
    val result = mutableMapOf<Pair<String, String>, String>()
    while (parser.eventType != XmlPullParser.END_DOCUMENT) {
        if (parser.eventType == XmlPullParser.START_TAG) {
            for (index in 0 until parser.attributeCount) {
                val key = parser.getAttributeNamespace(index) to parser.getAttributeName(index)
                if (key in CAPTURE_PROPERTIES) {
                    parser.getAttributeValue(index).trim().takeIf { it.isNotEmpty() }?.let { result[key] = it }
                }
            }
            val key = parser.namespace to parser.name
            if (key in CAPTURE_PROPERTIES) {
                parser.nextText().trim().takeIf { it.isNotEmpty() }?.let { result[key] = it }
            }
        }
        parser.next()
    }
    return result
}
