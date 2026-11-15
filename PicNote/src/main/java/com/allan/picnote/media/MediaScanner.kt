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
import com.allan.picnote.data.entity.*
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

data class ScanSummary(val baseline: Boolean, val added: Int, val scannedAt: Long)

/** 仅比较完整授权下最新时间段的新图片，卷重建与权限恢复只建立基线。 */
class MediaScanner(private val context: Context, private val database: PicNoteDatabase) {
    private val dao = database.dao()

    suspend fun permissionLost() = withContext(Dispatchers.IO) { dao.invalidateBaselines() }

    suspend fun pending(): List<PendingMediaEntity> = withContext(Dispatchers.IO) {
        if (photoAccess(context) == PhotoAccess.Full) dao.pending() else emptyList()
    }

    suspend fun ignore(uris: List<String>) = withContext(Dispatchers.IO) { dao.consumePending(uris) }

    suspend fun previousSummary(): ScanSummary? = withContext(Dispatchers.IO) {
        val states = dao.scanStates().filter { it.available && it.baselineValid }
        if (states.isEmpty()) null else {
            val changes = dao.changes()
            ScanSummary(changes.isEmpty(), changes.size, states.map { it.scannedAt }.sortedDescending().first())
        }
    }

    private fun volumes(): Set<String> = MediaStore.getExternalVolumeNames(context)

    private fun version(volume: String): String = if (Build.VERSION.SDK_INT >= 30) {
        MediaStore.getVersion(context, volume)
    } else MediaStore.getVersion(context)

    suspend fun scan(): ScanSummary = withContext(Dispatchers.IO) {
        check(photoAccess(context) == PhotoAccess.Full) { "需要完整图片权限才能扫描" }
        val beforeVolumes = volumes()
        val versions = beforeVolumes.associateWith { version(it) }
        val queried = beforeVolumes.associateWith { queryVolume(it) }
        currentCoroutineContext().ensureActive()
        fun validate() {
            check(photoAccess(context) == PhotoAccess.Full) { "图片权限已变化，请重新授权" }
            check(volumes() == beforeVolumes && beforeVolumes.all { version(it) == versions[it] }) { "图片库已变化，下次进入时重试" }
        }
        validate()
        database.withTransaction {
            currentCoroutineContext().ensureActive()
            validate()
            val states = dao.scanStates().associateBy { it.volume }
            val changes = mutableListOf<MediaChangeEntity>()
            var rebuilt = false
            val time = System.currentTimeMillis()
            beforeVolumes.forEach { volume ->
                val current = queried.getValue(volume)
                val prefix = MediaStore.Images.Media.getContentUri(volume).toString() + "/"
                val previousState = states[volume]
                val rebaseline = previousState == null || !previousState.baselineValid || !previousState.available || previousState.version != versions[volume]
                if (rebaseline) {
                    rebuilt = true
                    if (previousState == null || previousState.version != versions[volume]) dao.clearPendingVolume(volume)
                    if (previousState != null && previousState.version != versions[volume]) dao.clearSourceUris(prefix)
                } else {
                    val previous = dao.snapshots(prefix)
                    val oldUris = previous.map { it.uri }.toSet()
                    val latest = previous.firstOrNull()?.time ?: Long.MIN_VALUE
                    val added = current.takeWhile { it.snapshot.time >= latest }.filter { it.snapshot.uri !in oldUris }
                    changes += added.map { MediaChangeEntity(it.snapshot.uri, volume) }
                    dao.putPending(added.map { PendingMediaEntity(it.snapshot.uri, volume, it.name, it.snapshot.time) })
                }
                dao.clearSnapshots(prefix)
                dao.putSnapshots(current.map { it.snapshot })
                dao.cleanMissingPending(volume)
                dao.putScanStates(listOf(MediaScanStateEntity(volume, versions.getValue(volume), time, true, true)))
            }
            dao.putScanStates(states.values.filter { it.volume !in beforeVolumes }.map { it.copy(available = false, baselineValid = false) })
            dao.clearChanges()
            dao.putChanges(changes)
            currentCoroutineContext().ensureActive()
            validate()
            ScanSummary(rebuilt && changes.isEmpty(), changes.size, time)
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
                    ?: error("无法读取图片列表，请重试")
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
