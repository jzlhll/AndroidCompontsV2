package com.allan.mydroid.beansinner

import android.net.Uri
import androidx.annotation.Keep
import androidx.core.net.toUri
import com.allan.mydroid.R
import com.allan.mydroid.utils.JsonUriAdapter
import com.au.module_android.Globals
import com.au.module_android.utilsmedia.MediaTypeUtil
import com.au.module_android.utilsmedia.UriParsedInfo
import com.au.module_android.utilsmedia.VideoDurationHelper
import com.au.module_android.utilsmedia.formatBytes
import com.au.module_android.utilsmedia.myParseSuspend
import com.google.gson.annotations.JsonAdapter
import kotlinx.coroutines.delay
import java.util.UUID

const val FROM_LOCAL = "local"
const val FROM_SHARE_IN = "share_in"
const val FROM_PICKER = "picker"

@Keep
data class ShareInBean(val uriUuid:String,
                         @JsonAdapter(JsonUriAdapter::class)
                         val uri: Uri,
                         val from:String,
                         val mimeType:String,
                         val name:String? = null,
                         val fileSize:Long?,
                         val fileSizeStr:String,
                         val videoDuration:Long?) {

    /**
     * 是不是本地接收的文件
     */
    @Transient var isLocalReceiver = false
    @Transient var isNoDeleteBtn = false

    companion object {
        suspend fun convert(info: MergedFileInfo, from:String) : ShareInBean {
            delay(0)
            val fileSize = info.file.length()
            val fileLen = formatBytes(fileSize)
            val uriUuid = UUID.randomUUID().toString().replace("-", "")

            val mimeType = MediaTypeUtil.getMimeTypePath(info.file.absolutePath)
            val videoDuration = VideoDurationHelper().getDurationNormally(info.file.absolutePath, mimeType)
            return ShareInBean(uriUuid,
                info.file.toUri(),
                from,
                mimeType,
                info.file.name,
                fileSize,
                fileLen,
                videoDuration)
        }

        suspend fun convert(uri: Uri, from:String) : ShareInBean {
            delay(0)
            val parsedInfo = uri.myParseSuspend(Globals.app.contentResolver)
            return copyFrom(parsedInfo, from)
        }

        private suspend fun copyFrom(info: UriParsedInfo, from:String) : ShareInBean {
            delay(0)
            val uriUuid = UUID.randomUUID().toString().replace("-", "")
            val fileSize = info.fileLength
            val fileSizeStr = if(fileSize > 0) formatBytes(fileSize) else Globals.getString(R.string.unknown_size)
            return ShareInBean(uriUuid,
                info.uri,
                from,
                info.mimeType,
                info.name,
                fileSize,
                fileSizeStr,
                info.videoDuration)
        }

        fun copyFrom(info: ShareInBean) : ShareInBean {
            val uriUuid = UUID.randomUUID().toString().replace("-", "")
            return ShareInBean(uriUuid, info.uri,
                info.from,
                info.mimeType,
                info.name,
                info.fileSize,
                info.fileSizeStr,
                info.videoDuration)
        }
    }

    fun copyToHtml() : ShareInHtml {
        return ShareInHtml(uriUuid, name, fileSizeStr)
    }
}