package com.allan.mydroid.client.beans

import android.net.Uri
import androidx.annotation.Keep
import com.allan.mydroid.beansinner.ImportBatchItem

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
    val mimeType: String? = null,
    val from: String? = null,
    override val importBatchId: String? = null,
    override val importedAt: Long = 0L,
    override val importOrder: Int = 0
) : ImportBatchItem

/** 待上传文件项。 */
data class SelectedFile(
    val uri: Uri,
    val name: String,
    val size: Long,
    val id: String = java.util.UUID.randomUUID().toString(),
    val state: UploadState = UploadState.Pending,
    val progress: Float = 0f,
    val error: String? = null,
    val savedName: String? = null,
    val bytesPerSecond: Long = 0,
    val md5: String? = null
)

enum class UploadState { Pending, Preparing, Uploading, Merging, Completed, Failed, Canceled }
