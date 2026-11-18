package com.allan.picnote.media

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.CancellationSignal
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import com.allan.picnote.data.PicNoteDatabase
import com.allan.picnote.data.PicNoteStorageException
import com.allan.picnote.data.entity.*
import com.allan.picnote.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class PhotoAccess { Full, Partial, Denied }

fun photoAccess(context: Context): PhotoAccess {
    fun granted(name: String) = ContextCompat.checkSelfPermission(context, name) == PackageManager.PERMISSION_GRANTED
    return when {
        Build.VERSION.SDK_INT >= 33 && granted(Manifest.permission.READ_MEDIA_IMAGES) -> PhotoAccess.Full
        Build.VERSION.SDK_INT >= 34 && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> PhotoAccess.Partial
        Build.VERSION.SDK_INT <= 32 && granted(Manifest.permission.READ_EXTERNAL_STORAGE) -> PhotoAccess.Full
        else -> PhotoAccess.Denied
    }
}

fun photoPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

/** 仅比较完整授权下最新时间段的新图片，卷重建与权限恢复只建立基线。 */
class MediaScanner(private val context: Context, private val database: PicNoteDatabase) {
    private val dao = database.dao()

    suspend fun permissionLost() = withContext(Dispatchers.IO) { dao.invalidateBaselines() }

    suspend fun pending(): List<PendingMediaEntity> = withContext(Dispatchers.IO) {
        if (photoAccess(context) != PhotoAccess.Full) return@withContext emptyList()
        val versions = currentMediaVersions(context)
        dao.pending().filter { it.version == versions[it.volume] }
    }

    suspend fun ignoreAll() = withContext(Dispatchers.IO) { dao.clearPending() }

    private fun volumes(): Set<String> = MediaStore.getExternalVolumeNames(context)

    suspend fun scan(): Int = withContext(Dispatchers.IO) {
        if (photoAccess(context) != PhotoAccess.Full) throw PicNoteStorageException(R.string.error_full_photo_access_required)
        val beforeVolumes = volumes()
        val versions = beforeVolumes.associateWith { MediaStore.getVersion(context, it) }
        val queried = beforeVolumes.associateWith { queryVolume(it) }
        currentCoroutineContext().ensureActive()
        fun validate() {
            if (photoAccess(context) != PhotoAccess.Full) throw PicNoteStorageException(R.string.error_photo_access_changed)
            if (volumes() != beforeVolumes || beforeVolumes.any { MediaStore.getVersion(context, it) != versions[it] }) {
                throw PicNoteStorageException(R.string.error_media_library_changed)
            }
        }
        validate()
        database.withTransaction {
            currentCoroutineContext().ensureActive()
            validate()
            val states = dao.scanStates().associateBy { it.volume }
            var addedCount = 0
            val time = System.currentTimeMillis()
            beforeVolumes.forEach { volume ->
                val current = queried.getValue(volume)
                val version = versions.getValue(volume)
                val prefix = MediaStore.Images.Media.getContentUri(volume).toString() + "/"
                val previousState = states[volume]
                val previous = dao.snapshots(prefix)
                val rebaseline = previousState == null || !previousState.baselineValid || !previousState.available || previousState.version != version
                if (rebaseline) {
                    if (previousState == null || previousState.version != version) dao.clearPendingVolume(volume)
                } else {
                    val oldUris = previous.associate { it.uri to it.time }
                    val latest = previous.firstOrNull()?.time ?: Long.MIN_VALUE
                    val added = current.takeWhile { it.snapshot.time >= latest }.filter { oldUris[it.snapshot.uri] != it.snapshot.time }
                    addedCount += added.size
                    dao.putPending(added.map { PendingMediaEntity(it.snapshot.uri, volume, it.name, it.snapshot.time, version) })
                }
                val snapshots = current.map { it.snapshot }
                if (previous != snapshots) {
                    dao.clearSnapshots(prefix)
                    dao.putSnapshots(snapshots)
                }
                dao.cleanMissingPending(volume)
                dao.putScanStates(listOf(MediaScanStateEntity(volume, version, time, true, true)))
            }
            dao.putScanStates(states.values.filter { it.volume !in beforeVolumes && (it.available || it.baselineValid) }
                .map { it.copy(available = false, baselineValid = false) })
            currentCoroutineContext().ensureActive()
            validate()
            addedCount
        }
    }

    private data class QueriedImage(val snapshot: MediaSnapshotEntity, val name: String)

    private suspend fun queryVolume(volume: String): List<QueriedImage> {
        val uri = MediaStore.Images.Media.getContentUri(volume)
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media.DISPLAY_NAME)
        val coroutineContext = currentCoroutineContext()
        return suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            try {
                val cursor = context.contentResolver.query(uri, projection, null, null,
                    "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media._ID}", signal)
                    ?: throw PicNoteStorageException(R.string.error_read_image_list)
                val result = cursor.use {
                    val images = mutableListOf<QueriedImage>()
                    while (it.moveToNext()) {
                        coroutineContext.ensureActive()
                        images += QueriedImage(MediaSnapshotEntity(ContentUris.withAppendedId(uri, it.getLong(0)).toString(), it.getLong(1)), it.getString(2).orEmpty())
                    }
                    images.sortedWith(compareByDescending<QueriedImage> { image -> image.snapshot.time }.thenBy { image -> image.snapshot.uri })
                }
                continuation.resume(result)
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }
}
