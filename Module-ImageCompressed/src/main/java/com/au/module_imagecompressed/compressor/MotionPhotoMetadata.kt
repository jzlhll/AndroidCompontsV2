package com.au.module_imagecompressed.compressor

import android.util.Xml
import androidx.exifinterface.media.ExifInterface
import org.xmlpull.v1.XmlPullParser

private const val CAMERA_NAMESPACE = "http://ns.google.com/photos/1.0/camera/"
private const val ITEM_NAMESPACE = "http://ns.google.com/photos/1.0/container/item/"

/** 按命名空间识别动态照片标记，兼容属性和元素两种 XMP 表达。 */
internal fun ExifInterface.hasMotionPhotoMetadata(): Boolean {
    val xmp = getAttributeBytes(ExifInterface.TAG_XMP) ?: return false
    val parser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        setInput(xmp.inputStream(), null)
    }
    // 正向标记即要求执行携带或移除策略，残留标记也不能触发含视频原图回退。
    while (parser.eventType != XmlPullParser.END_DOCUMENT) {
        if (parser.eventType == XmlPullParser.START_TAG) {
            for (index in 0 until parser.attributeCount) {
                if (isMotionPhotoProperty(
                        parser.getAttributeNamespace(index),
                        parser.getAttributeName(index),
                        parser.getAttributeValue(index),
                    )
                ) {
                    return true
                }
            }
            val namespace = parser.namespace
            val name = parser.name
            val isCameraProperty = namespace == CAMERA_NAMESPACE &&
                (name == "MotionPhoto" || name == "MicroVideo" || name == "MicroVideoOffset")
            if (isCameraProperty || namespace == ITEM_NAMESPACE && name == "Semantic") {
                if (isMotionPhotoProperty(namespace, name, parser.nextText())) return true
            }
        }
        parser.next()
    }
    return false
}

// 只依据命名空间和字段语义判断，避免不同 XML 前缀影响识别。
private fun isMotionPhotoProperty(namespace: String, name: String, value: String): Boolean {
    val normalized = value.trim()
    return when (namespace) {
        CAMERA_NAMESPACE -> when (name) {
            "MotionPhoto", "MicroVideo" -> normalized.toIntOrNull() == 1
            "MicroVideoOffset" -> (normalized.toLongOrNull() ?: 0L) > 0L
            else -> false
        }
        ITEM_NAMESPACE -> name == "Semantic" && normalized == "MotionPhoto"
        else -> false
    }
}
