package com.allan.picnote.media

import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.allan.picnote.R
import com.allan.picnote.data.entity.PendingMediaEntity
import com.au.module_android.Globals
import com.au.module_android.utils.withIOThread
import com.au.module_android.utilsmedia.deleteSystemAlbumImages
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class MediaDeletionBatch(val images: List<PendingMediaEntity>, val versions: Map<String, String>)

/** 仅支持 Android 12 及以上，校验已保存批次的源图片身份后发起系统删除确认。 */
class MediaDeletion(private val context: Context) {
    suspend fun delete(batch: MediaDeletionBatch, requestConfirmation: suspend (IntentSender) -> Boolean): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            for (images in batch.images.chunked(2000)) {
                val uris = withIOThread {
                    currentCoroutineContext().ensureActive()
                    check(photoAccess(context) == PhotoAccess.Full) { Globals.app.getString(R.string.error_photo_access_changed) }
                    images.map { it.volume }.distinct().forEach { volume ->
                        check(MediaStore.getVersion(context, volume) == batch.versions[volume]) {
                            Globals.app.getString(R.string.error_media_library_changed)
                        }
                    }
                    images.mapNotNull { image ->
                        currentCoroutineContext().ensureActive()
                        val uri = Uri.parse(image.uri)
                        val id = ContentUris.parseId(uri)
                        check(ContentUris.withAppendedId(MediaStore.Images.Media.getContentUri(image.volume), id) == uri) {
                            Globals.app.getString(R.string.error_media_library_changed)
                        }
                        val cursor = context.contentResolver.query(uri, arrayOf(MediaStore.Images.Media.DATE_ADDED), null, null, null)
                            ?: error(Globals.app.getString(R.string.error_read_image_list))
                        cursor.use {
                            if (!cursor.moveToFirst()) null else {
                                check(cursor.getLong(0) == image.addedAt) { Globals.app.getString(R.string.error_media_library_changed) }
                                uri
                            }
                        }
                    }
                }
                if (!deleteSystemAlbumImages(context, uris, requestConfirmation)) return false
            }
            return true
        }
        throw UnsupportedOperationException("Deleting system album images requires Android 12 or later")
    }
}
