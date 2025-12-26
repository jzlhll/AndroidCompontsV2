package com.au.module_imagecompressed

import com.au.module_android.utilsmedia.UriParsedInfo
import kotlinx.serialization.Serializable

/**
 * @param beLimitedSize null表示没有进行过size check；被限制了size则会被标记，那么也意味着是原始的Uri。
 * @param beCopied 被拷贝则代表这个Uri是File型的。
 *
 */
@Serializable
data class PickUriWrap(var uriParsedInfo: UriParsedInfo,
                       var totalNum:Int,
                       var isImage:Boolean,
                       var beLimitedSize:Boolean = false,
                       var beCopied:Boolean = false) {
    override fun toString(): String {
        return "${uriParsedInfo.uri}," +
                " ${uriParsedInfo.name}" +
                ", ${uriParsedInfo.fileLength}," +
                " ${uriParsedInfo.mimeType} " +
                "beCopied $beCopied " +
                "beLimitedSize $beLimitedSize"
    }
}