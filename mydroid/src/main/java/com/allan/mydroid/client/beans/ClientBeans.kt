package com.allan.mydroid.client.beans

import android.net.Uri
import androidx.annotation.Keep

/**
 * host 端 /request-file-list 返回项的精简结构。
 * host 返回的 ShareInBean 含 uri/mimeType/videoDuration/from 等字段，client 端只取需要的，
 * Gson 会自动忽略未声明的字段，无需担心 host 端 JsonUriAdapter 影响。
 */
@Keep
data class RemoteFileBean(
    val name: String? = null,
    val fileSizeStr: String = "",
    val uriUuid: String = "",
    val fileSize: Long? = null,
    val mimeType: String? = null
)

/** 聊天消息 UI 项。 */
data class ChatMessage(
    val text: String,
    val isMe: Boolean,
    val timestamp: Long,
    val iconColor: String
)

/** 待上传文件项。 */
data class SelectedFile(
    val uri: Uri,
    val name: String,
    val size: Long
)
