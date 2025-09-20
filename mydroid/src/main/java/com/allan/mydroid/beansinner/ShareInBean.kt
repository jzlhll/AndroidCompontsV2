package com.allan.mydroid.beansinner

import android.net.Uri
import androidx.annotation.Keep
import androidx.core.net.toUri
import com.allan.mydroid.R
import com.allan.mydroid.utils.JsonUriAdapter
import com.au.module_android.Globals
import com.au.module_android.utilsmedia.UriRealInfo
import com.au.module_android.utilsmedia.formatBytes
import com.au.module_android.utilsmedia.getRealInfo
import com.au.module_android.utilsmedia.length
import com.google.gson.annotations.JsonAdapter
import kotlinx.coroutines.delay
import java.io.File
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
            val realInfo = uri.getRealInfo(Globals.app)
            return copyFrom(realInfo)
        }

        private fun copyFrom(info: UriRealInfo) : ShareInBean {
            val goodPath = info.realPath ?: info.relativePath
            val goodPathLength = if(goodPath != null) File(goodPath).length() else 0
            val fileSize = if(goodPathLength == 0L) info.uri.length(Globals.app.contentResolver) else goodPathLength
            val fileLen = if(fileSize > 0) formatBytes(fileSize) else Globals.getString(R.string.unknown_size)
            val uriUuid = UUID.randomUUID().toString().replace("-", "")
            return ShareInBean(uriUuid, info.uri, info.goodName(), fileSize, fileLen)
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