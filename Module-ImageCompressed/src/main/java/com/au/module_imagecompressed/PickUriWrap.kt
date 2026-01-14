package com.au.module_imagecompressed

import androidx.annotation.Keep
import com.au.module_android.utilsmedia.UriParsedInfo
import kotlinx.serialization.Serializable

/**
 * @param beCopied 代表是否经历过压缩或者拷贝的
 */
@Serializable
@Keep
data class PickUriWrap(var uriParsedInfo: UriParsedInfo,
                       var totalNum:Int,
                       var isImage:Boolean,
                       var beCopied:Boolean = false) {
    override fun toString(): String {
        return "${uriParsedInfo.uri}," +
                " ${uriParsedInfo.name}" +
                ", ${uriParsedInfo.fileLength}," +
                " ${uriParsedInfo.mimeType} " +
                "beCopied $beCopied "
    }
}