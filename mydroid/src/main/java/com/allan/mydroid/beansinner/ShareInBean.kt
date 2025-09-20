package com.allan.mydroid.beansinner

import android.net.Uri
import androidx.annotation.Keep
import androidx.core.net.toUri
import com.allan.mydroid.R
import com.allan.mydroid.utils.JsonUriAdapter
import com.au.module_android.Globals
import com.au.module_android.utilsmedia.UriParsedInfo
import com.au.module_android.utilsmedia.UriParserUtil
import com.au.module_android.utilsmedia.formatBytes
import com.google.gson.annotations.JsonAdapter
import kotlinx.coroutines.delay
import java.util.UUID

@Keep
data class ShareInBean(val uriUuid:String,
                         @JsonAdapter(JsonUriAdapter::class)
                         val uri: Uri,
                         val name:String? = null,
                         val fileSize:Long?,
                         val fileSizeStr:String) {

    /**
     * 是不是本地接收的文件
     */
    @Transient var isLocalReceiver = false

    companion object {
        fun to(info: MergedFileInfo) : ShareInBean {
            val fileSize = info.file.length()
            val fileLen = formatBytes(fileSize)
            val uriUuid = UUID.randomUUID().toString().replace("-", "")
            return ShareInBean(uriUuid, info.file.toUri(), info.file.name, fileSize, fileLen)
        }

        suspend fun to(uri: Uri) : ShareInBean {
            delay(0)
            val parsedInfo = UriParserUtil(uri).parseSuspend(Globals.app.contentResolver)
            return copyFrom(parsedInfo)
        }

        private suspend fun copyFrom(info: UriParsedInfo) : ShareInBean {
            delay(0)
            val uriUuid = UUID.randomUUID().toString().replace("-", "")
            val fileSize = info.fileLength
            val fileSizeStr = if(fileSize > 0) formatBytes(fileSize) else Globals.getString(R.string.unknown_size)
            return ShareInBean(uriUuid,
                info.uri,
                info.name,
                fileSize,
                fileSizeStr)
        }

        fun copyFrom(info: ShareInBean) : ShareInBean {
            val uriUuid = UUID.randomUUID().toString().replace("-", "")
            return ShareInBean(uriUuid, info.uri, info.name, info.fileSize, info.fileSizeStr)
        }
    }

    fun copyToHtml() : ShareInHtml {
        return ShareInHtml(uriUuid, name, fileSizeStr)
    }
}