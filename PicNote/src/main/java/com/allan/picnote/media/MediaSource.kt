package com.allan.picnote.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.allan.picnote.R
import com.allan.picnote.data.PicNoteStorageException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

const val MediaQueryBatchSize = 900

data class MediaSource(val volume: String, val id: Long)
data class MediaSourceStamp(val volume: String, val version: String, val addedAt: Long)

/** 只识别具体卷的规范 MediaStore 图片 URI，Photo Picker URI 保持原样。 */
fun mediaSource(value: String): MediaSource? {
    val uri = Uri.parse(value)
    if (uri.scheme != "content" || uri.authority != MediaStore.AUTHORITY) return null
    val path = uri.pathSegments
    val id = path.getOrNull(3)?.toLongOrNull() ?: return null
    if (path.size != 4 || path[1] != "images" || path[2] != "media" || id <= 0L) return null
    val volume = path[0]
    val expected = ContentUris.withAppendedId(MediaStore.Images.Media.getContentUri(volume), id)
    return if (value == expected.toString()) MediaSource(volume, id) else null
}

fun currentMediaVersions(context: Context, requestedVolumes: Set<String>? = null): Map<String, String> = buildMap {
    val mounted = MediaStore.getExternalVolumeNames(context)
    val volumes = if (requestedVolumes == null) mounted else mounted.intersect(requestedVolumes)
    for (volume in volumes) {
        // 卷在枚举后卸载时不再使用它的 URI。
        val version = try { MediaStore.getVersion(context, volume) } catch (_: IllegalArgumentException) { null }
        if (version != null) put(volume, version)
    }
}

suspend fun mediaAddedTimes(context: Context, volume: String, ids: Collection<Long>): Map<Long, Long> = buildMap {
    val collection = MediaStore.Images.Media.getContentUri(volume)
    val idColumn = MediaStore.Images.Media._ID
    val projection = arrayOf(idColumn, MediaStore.Images.Media.DATE_ADDED)
    ids.distinct().chunked(MediaQueryBatchSize).forEach { chunk ->
        currentCoroutineContext().ensureActive()
        val placeholders = chunk.joinToString(",") { "?" }
        val args = chunk.map { it.toString() }.toTypedArray()
        val cursor = context.contentResolver.query(collection, projection, "$idColumn IN ($placeholders)", args, null)
            ?: throw PicNoteStorageException(R.string.error_read_image_list)
        cursor.use {
            while (it.moveToNext()) put(it.getLong(0), it.getLong(1))
        }
    }
}
