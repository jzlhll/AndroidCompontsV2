package com.allan.picnote.media

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import com.allan.picnote.R
import com.allan.picnote.data.PicNoteStorageException
import com.allan.picnote.data.entity.PendingMediaEntity
import com.au.module_android.utilsmedia.deleteSystemAlbumImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class MediaDeletionBatch(val images: List<PendingMediaEntity>)

/** 仅支持 Android 12 及以上，校验已保存批次的源图片身份后发起系统删除确认。 */
class MediaDeletion(private val context: Context) {
    suspend fun delete(batch: MediaDeletionBatch, requestConfirmation: suspend (IntentSender) -> Boolean): Boolean {
        val frozen = batch.images.associateBy { Uri.parse(it.uri) }
        return try {
            deleteSystemAlbumImages(
                context,
                frozen.keys.toList(),
                verifyBatch = { uris -> verifiedUris(uris.map { frozen.getValue(it) }) },
                requestConfirmation = { sender -> withContext(Dispatchers.Main.immediate) { requestConfirmation(sender) } },
            )
        } catch (e: SecurityException) {
            throw PicNoteStorageException(R.string.error_photo_access_changed, e)
        }
    }

    private suspend fun verifiedUris(images: List<PendingMediaEntity>): List<Uri> {
        val byVolume = images.groupBy { it.volume }
        fun validateVolumes() {
            if (photoAccess(context) != PhotoAccess.Full) throw PicNoteStorageException(R.string.error_photo_access_changed)
            // 普通删除改变媒体代数，只有卷版本变化才让冻结 URI 的身份失效。
            val versions = currentMediaVersions(context, byVolume.keys)
            if (images.any { it.version != versions[it.volume] }) {
                throw PicNoteStorageException(R.string.error_media_library_changed)
            }
        }
        validateVolumes()
        val existing = mutableSetOf<String>()
        for ((volume, candidates) in byVolume) {
            val withIds = candidates.map { image ->
                val source = mediaSource(image.uri)
                if (source == null || source.volume != volume) throw PicNoteStorageException(R.string.error_media_library_changed)
                image to source.id
            }
            val current = try {
                mediaAddedTimes(context, volume, withIds.map { it.second })
            } catch (e: IllegalArgumentException) {
                try {
                    validateVolumes()
                } catch (changed: PicNoteStorageException) {
                    throw PicNoteStorageException(changed.messageRes, e)
                }
                throw e
            }
            for ((image, id) in withIds) {
                val addedAt = current[id] ?: continue
                if (addedAt != image.addedAt) throw PicNoteStorageException(R.string.error_media_library_changed)
                existing += image.uri
            }
        }
        currentCoroutineContext().ensureActive()
        validateVolumes()
        return images.filter { it.uri in existing }.map { Uri.parse(it.uri) }
    }
}
