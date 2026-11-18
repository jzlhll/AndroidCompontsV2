package com.au.module_android.utilsmedia

import android.content.ContentResolver
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.au.module_android.utils.withIOThread
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * 仅支持 Android 12（API 31）及以上，按系统确认结果删除相册中的图片。
 * 调用前须完成应用内副本保存；只接受具体 MediaStore 图片 URI，不接受 Photo Picker URI 或文件路径。
 * requestConfirmation 应启动 IntentSender 并等待 Activity 结果，只有 RESULT_OK 才返回 true。
 * 用户取消时停止后续批次，已完成的删除不会回滚；异常与取消交由调用方处理。
 */
@RequiresApi(Build.VERSION_CODES.S)
suspend fun deleteSystemAlbumImages(
    context: Context,
    uris: List<Uri>,
    requestConfirmation: suspend (IntentSender) -> Boolean,
): Boolean = deleteSystemAlbumImages(context, uris, { it }, requestConfirmation)

/** 在每个系统确认前复核本批 URI，允许跳过已不存在的图片。 */
@RequiresApi(Build.VERSION_CODES.S)
suspend fun deleteSystemAlbumImages(
    context: Context,
    uris: List<Uri>,
    verifyBatch: suspend (List<Uri>) -> Collection<Uri>,
    requestConfirmation: suspend (IntentSender) -> Boolean,
): Boolean {
    require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { "Deleting system album images requires Android 12 or later" }
    val images = uris.distinct()
    require(images.all { uri ->
        val parts = uri.pathSegments
        val id = parts.lastOrNull()?.toLongOrNull()
        uri.scheme == ContentResolver.SCHEME_CONTENT && uri.authority == MediaStore.AUTHORITY &&
            parts.size == 4 && parts[1] == "images" && parts[2] == "media" && id != null && id > 0
    }) { "Only specific MediaStore image URIs can be deleted" }
    for (batch in images.chunked(2000)) {
        currentCoroutineContext().ensureActive()
        val sender = withIOThread {
            val verified = verifyBatch(batch).toSet()
            val remaining = batch.filter { it in verified }
            currentCoroutineContext().ensureActive()
            if (remaining.isEmpty()) null
            else MediaStore.createDeleteRequest(context.contentResolver, remaining).intentSender
        }
        if (sender != null && !requestConfirmation(sender)) return false
    }
    return true
}
