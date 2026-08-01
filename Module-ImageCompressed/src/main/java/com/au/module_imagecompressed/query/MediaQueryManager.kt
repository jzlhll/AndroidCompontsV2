package com.au.module_imagecompressed.query

import android.content.ContentUris
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 所有查询方法均在IO线程执行，推荐协程调用
 */
class MediaQueryManager(private val context: Context) {
    private val contentResolver by lazy { context.contentResolver }
    private val cameraRelativePath = "${Environment.DIRECTORY_DCIM}/Camera/"
    private val screenshotRelativePath =
        "${Environment.DIRECTORY_PICTURES}/${Environment.DIRECTORY_SCREENSHOTS}/"
    private val screenshotDirectoryName = Environment.DIRECTORY_SCREENSHOTS

    // ==================== 1. 查询所有相册列表 ====================
    suspend fun queryAllAlbums(ignoreScreenshots: Boolean = false): List<Album> = withContext(Dispatchers.IO) {
        val albumMap = mutableMapOf<Long, Album>()

        val projection = arrayOf(
            FileColumns.BUCKET_ID,
            FileColumns.BUCKET_DISPLAY_NAME,
            FileColumns._ID,              // 用于生成封面 Uri
            FileColumns.MEDIA_TYPE,       // 用于区分 Uri 类型
            FileColumns.DATE_MODIFIED,
            FileColumns.RELATIVE_PATH
        )

        val selectionBuilder = StringBuilder("${FileColumns.MEDIA_TYPE} IN (?, ?)")
        val selectionArgs = mutableListOf(
            FileColumns.MEDIA_TYPE_IMAGE.toString(),
            FileColumns.MEDIA_TYPE_VIDEO.toString()
        )
        addScreenshotFilter(selectionBuilder, selectionArgs, ignoreScreenshots)

        // 按时间逆序，这样 map 中记录的第一个就是该相册最新的图，适合做封面
        val sortOrder = "${FileColumns.DATE_MODIFIED} DESC"

        contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selectionBuilder.toString(),
            selectionArgs.toTypedArray(),
            sortOrder
        )?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(FileColumns._ID)
            val typeIdx = c.getColumnIndexOrThrow(FileColumns.MEDIA_TYPE)
            val bucketIdIdx = c.getColumnIndexOrThrow(FileColumns.BUCKET_ID)
            val nameIdx = c.getColumnIndexOrThrow(FileColumns.BUCKET_DISPLAY_NAME)
            val relativePathIdx = c.getColumnIndexOrThrow(FileColumns.RELATIVE_PATH)

            while (c.moveToNext()) {
                val bucketId = c.getLong(bucketIdIdx)
                val mediaId = c.getLong(idIdx)
                val mediaType = c.getInt(typeIdx)
                val relativePath = c.getString(relativePathIdx)

                val album = albumMap[bucketId]
                if (album == null) {
                    // 生成封面 Uri
                    val baseUri = if (mediaType == FileColumns.MEDIA_TYPE_VIDEO)
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

                    val name = c.getString(nameIdx) ?: "Others"
                    albumMap[bucketId] = Album(
                        bucketId = bucketId,
                        name = name,
                        coverUri = ContentUris.withAppendedId(baseUri, mediaId),
                        count = 1,
                        isCamera = isCameraRelativePath(relativePath)
                    )
                } else {
                    album.count++ // 已经在 Map 里了，累加计数
                }
            }
        }
        return@withContext albumMap.values.toList().sortedByDescending { it.count }
    }

    // ==================== 2. 查询所有图片+视频（支持指定相册，时间逆序）- 仍用Files通用Uri ====================
    suspend fun queryAllImageAndVideo(
        album: Album? = null,
        limit: Int? = null,
        ignoreScreenshots: Boolean = false
    ): List<MediaFile> = withContext(Dispatchers.IO) {
        val mediaList = mutableListOf<MediaFile>()
        // 图片/视频通用投影列
        val projection = arrayOf(
            FileColumns._ID,
            FileColumns.DISPLAY_NAME,
            FileColumns.MIME_TYPE,
            FileColumns.SIZE,
            FileColumns.DATE_MODIFIED,
            FileColumns.BUCKET_ID,
            FileColumns.DURATION,
            FileColumns.WIDTH,
            FileColumns.HEIGHT,
            FileColumns.ORIENTATION,
            FileColumns.RELATIVE_PATH,
        )
        // 构建筛选条件：媒体类型 + 可选相册
        val selectionBuilder = StringBuilder()
        val selectionArgs = mutableListOf<String>()

        // 筛选媒体类型
        val mediaTypes = listOf(FileColumns.MEDIA_TYPE_IMAGE, FileColumns.MEDIA_TYPE_VIDEO)
        selectionBuilder.append("${FileColumns.MEDIA_TYPE} IN (${mediaTypes.joinToString(",") { "?" }})")
        mediaTypes.forEach { selectionArgs.add(it.toString()) }
        // 筛选指定相册
        addAlbumFilter(selectionBuilder, selectionArgs, album)
        addScreenshotFilter(selectionBuilder, selectionArgs, ignoreScreenshots)

        val baseSortOrder = "${FileColumns.DATE_MODIFIED} DESC"
        val sortOrder = if (limit != null && limit > 0) "$baseSortOrder LIMIT $limit" else baseSortOrder

        val cursor = contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selectionBuilder.toString(),
            selectionArgs.toTypedArray(),
            sortOrder
        )
        // 解析Cursor为MediaFile
        cursor?.use { mediaList.addAll(parseMediaCursor(it)) }
        return@withContext mediaList
    }

    // ==================== 3. 查询所有图片（支持指定相册，时间逆序）- 改用MediaStore.Images专属Uri ====================
    suspend fun queryAllImages(
        album: Album? = null,
        limit: Int? = null,
        ignoreScreenshots: Boolean = false
    ): List<MediaFile> = withContext(Dispatchers.IO) {
        val contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.ORIENTATION,
            MediaStore.Images.Media.RELATIVE_PATH
        )
        val selectionBuilder = StringBuilder()
        val selectionArgs = mutableListOf<String>()

        // 仅筛选指定相册，无需筛选媒体类型（专属Uri已限定）
        addAlbumFilter(selectionBuilder, selectionArgs, album)
        addScreenshotFilter(selectionBuilder, selectionArgs, ignoreScreenshots)

        val cursor: Cursor? = queryMediaWithLimit(
            contentUri = contentUri,
            projection = projection,
            selection = if (selectionBuilder.isNotEmpty()) selectionBuilder.toString() else null,
            selectionArgs = if (selectionArgs.isNotEmpty()) selectionArgs.toTypedArray() else null,
            sortColumn = MediaStore.Images.Media.DATE_MODIFIED,
            limit = limit
        )
        // 解析Cursor为MediaFile
        val mediaList = mutableListOf<MediaFile>()
        cursor?.use {
            mediaList.addAll(parseMediaCursor(it))
        }
        return@withContext mediaList
    }

    // ==================== 4. 查询所有视频（支持指定相册，时间逆序）- 改用MediaStore.Video专属Uri ====================
    suspend fun queryAllVideos(
        album: Album? = null,
        limit: Int? = null,
        ignoreScreenshots: Boolean = false
    ): List<MediaFile> = withContext(Dispatchers.IO) {
        val contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

        val mediaList = mutableListOf<MediaFile>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.ORIENTATION,
            MediaStore.Video.Media.RELATIVE_PATH,
        )
        val selectionBuilder = StringBuilder()
        val selectionArgs = mutableListOf<String>()

        // 仅筛选指定相册，无需筛选媒体类型（专属Uri已限定）
        addAlbumFilter(selectionBuilder, selectionArgs, album)
        addScreenshotFilter(selectionBuilder, selectionArgs, ignoreScreenshots)

        val cursor: Cursor? = queryMediaWithLimit(
            contentUri = contentUri,
            projection = projection,
            selection = if (selectionBuilder.isNotEmpty()) selectionBuilder.toString() else null,
            selectionArgs = if (selectionArgs.isNotEmpty()) selectionArgs.toTypedArray() else null,
            sortColumn = MediaStore.Video.Media.DATE_MODIFIED,
            limit = limit
        )
        // 解析Cursor为MediaFile
        cursor?.use {
            mediaList.addAll(parseMediaCursor(it))
        }
        return@withContext mediaList
    }

    // ==================== 5. 查询所有音频 ====================
    suspend fun queryAllAudios(): List<AudioFile> = withContext(Dispatchers.IO) {
        val audioList = mutableListOf<AudioFile>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.MIME_TYPE
        )
        // 排序：修改时间逆序
        val sortOrder = "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"

        val cursor: Cursor? = contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )

        cursor?.use { c ->
            // 全量替换为getColumnIndex，避免抛出异常
            val idIndex = c.getColumnIndex(MediaStore.Audio.Media._ID)
            val nameIndex = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val artistIndex = c.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            val albumIndex = c.getColumnIndex(MediaStore.Audio.Media.ALBUM)
            val durationIndex = c.getColumnIndex(MediaStore.Audio.Media.DURATION)
            val sizeIndex = c.getColumnIndex(MediaStore.Audio.Media.SIZE)
            val modifyTimeIndex = c.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
            val mimeTypeIndex = c.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)

            while (c.moveToNext()) {
                // 索引≥0才取值，否则给默认值，保证空安全
                val id = if (idIndex >= 0) c.getLong(idIndex) else 0L
                val rawName = if (nameIndex >= 0) c.getString(nameIndex) else null
                val name = rawName?.substringBeforeLast(".")
                val mimeType = if (mimeTypeIndex >= 0) c.getString(mimeTypeIndex) else null
                val rawArtist = if (artistIndex >= 0) c.getString(artistIndex) else null
                val artist = if (rawArtist == MediaStore.UNKNOWN_STRING || rawArtist.isNullOrEmpty()) "Unknown Artist" else rawArtist
                val rawAlbum = if (albumIndex >= 0) c.getString(albumIndex) else null
                val albumName = if (rawAlbum == MediaStore.UNKNOWN_STRING || rawAlbum.isNullOrEmpty()) "Unknown Album" else rawAlbum
                val duration = if (durationIndex >= 0) c.getLong(durationIndex) else 0L
                val size = if (sizeIndex >= 0) c.getLong(sizeIndex) else 0L
                val modifyTime = if (modifyTimeIndex >= 0) c.getLong(modifyTimeIndex) else 0L

                audioList.add(
                    AudioFile(
                        id = id,
                        name = name,
                        mimeType = mimeType,
                        artist = artist,
                        album = albumName,
                        duration = duration,
                        size = size,
                        modifyTime = modifyTime
                    )
                )
            }
        }
        return@withContext audioList
    }

    /**
     * 公共方法：添加相册筛选条件（复用逻辑，避免重复代码）
     */
    private fun addAlbumFilter(
        builder: StringBuilder,
        args: MutableList<String>,
        album: Album?
    ) {
        album?.let {
            val bucketId = it.bucketId
            val name = it.name
            if (bucketId >= 0) {
                if (builder.isNotEmpty()) builder.append(" AND ")
                builder.append("${MediaStore.MediaColumns.BUCKET_ID} = ?")
                args.add(bucketId.toString())
            } else if (!name.isNullOrEmpty()) {
                if (builder.isNotEmpty()) builder.append(" AND ")
                builder.append("${MediaStore.MediaColumns.BUCKET_DISPLAY_NAME} LIKE ?")
                args.add("%$name%")
            }
        }
    }

    private fun addScreenshotFilter(
        builder: StringBuilder,
        args: MutableList<String>,
        ignoreScreenshots: Boolean
    ) {
        if (!ignoreScreenshots) return
        if (builder.isNotEmpty()) builder.append(" AND ")

        val relativePathColumn = MediaStore.MediaColumns.RELATIVE_PATH
        builder.append(
            "($relativePathColumn IS NULL OR " +
                "($relativePathColumn NOT LIKE ? AND " +
                "$relativePathColumn NOT LIKE ? AND " +
                "$relativePathColumn NOT LIKE ?))"
        )
        args.add("$screenshotRelativePath%")
        args.add("$screenshotDirectoryName/%")
        args.add("%/$screenshotDirectoryName/%")
    }

    private fun isCameraRelativePath(relativePath: String?): Boolean {
        return relativePath?.startsWith(cameraRelativePath, ignoreCase = true) == true
    }

    private fun isScreenshotRelativePath(relativePath: String?): Boolean {
        if (relativePath.isNullOrEmpty()) return false
        return relativePath.startsWith(screenshotRelativePath, ignoreCase = true) ||
            relativePath.startsWith("$screenshotDirectoryName/", ignoreCase = true) ||
            relativePath.contains("/$screenshotDirectoryName/", ignoreCase = true)
    }

    /**
     * 公共方法：解析Cursor为MediaFile列表（全量安全取值，适配所有媒体查询）
     */
    private fun parseMediaCursor(cursor: Cursor): List<MediaFile> {
        val mediaList = mutableListOf<MediaFile>()
        // 获取所有列索引（全量使用getColumnIndex）
        val idIndex = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
        val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
        val mimeTypeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
        val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
        val modifyTimeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
        val bucketIdIndex = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_ID)
        val durationIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DURATION)
        val widthIndex = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
        val heightIndex = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
        val orientationIndex = cursor.getColumnIndex(MediaStore.MediaColumns.ORIENTATION)
        val relativePathIndex = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)

        while (cursor.moveToNext()) {
            val id = cursor.getLongOrNull(idIndex) ?: continue
            val rawName = cursor.getStringOrNull(nameIndex)
            val name = rawName?.substringBeforeLast(".")
            val mimeType = cursor.getStringOrNull(mimeTypeIndex)
            val size = cursor.getLongOrNull(sizeIndex) ?: 0L
            val duration = cursor.getLongOrNull(durationIndex) ?: 0L
            val modifyTime = cursor.getLongOrNull(modifyTimeIndex) ?: 0L
            val bucketId = cursor.getLongOrNull(bucketIdIndex)
            val width = cursor.getIntOrNull(widthIndex) ?: 0
            val height = cursor.getIntOrNull(heightIndex) ?: 0
            val orientation = cursor.getIntOrNull(orientationIndex)
            val relativePath = cursor.getStringOrNull(relativePathIndex)

            mediaList.add(
                MediaFile(
                    id = id,
                    name = name,
                    mimeType = mimeType,
                    size = size,
                    duration = duration,
                    modifyTime = modifyTime,
                    bucketId = bucketId,
                    width = width,
                    height = height,
                    orientation = orientation,
                    isCamera = isCameraRelativePath(relativePath),
                    isScreenshot = isScreenshotRelativePath(relativePath)
                )
            )
        }
        return mediaList
    }

    private fun queryMediaWithLimit(
        contentUri: android.net.Uri,
        projection: Array<String>,
        selection: String?,
        selectionArgs: Array<String>?,
        sortColumn: String,
        limit: Int?
    ): Cursor? {
        val normalizedLimit = limit?.takeIf { it > 0 }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val queryArgs = Bundle().apply {
                if (!selection.isNullOrEmpty()) putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                if (!selectionArgs.isNullOrEmpty()) putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "$sortColumn DESC")
                normalizedLimit?.let { putInt(ContentResolver.QUERY_ARG_LIMIT, it) }
            }
            contentResolver.query(contentUri, projection, queryArgs, null)
        } else {
            val sortOrder = if (normalizedLimit != null) "$sortColumn DESC LIMIT $normalizedLimit" else "$sortColumn DESC"
            contentResolver.query(contentUri, projection, selection, selectionArgs, sortOrder)
        }
    }

    private fun Cursor.getStringOrNull(columnIndex: Int): String? {
        if (columnIndex < 0) return null
        return try {
            if (getType(columnIndex) == Cursor.FIELD_TYPE_STRING) getString(columnIndex) else null
        } catch (_: Exception) {
            null
        }
    }

    private fun Cursor.getLongOrNull(columnIndex: Int): Long? {
        if (columnIndex < 0) return null
        return try {
            when (getType(columnIndex)) {
                Cursor.FIELD_TYPE_INTEGER -> getLong(columnIndex)
                Cursor.FIELD_TYPE_STRING -> getString(columnIndex)?.toLongOrNull()
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun Cursor.getIntOrNull(columnIndex: Int): Int? {
        val value = getLongOrNull(columnIndex) ?: return null
        return if (value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) value.toInt() else null
    }
}
/*
fun test() {
        Globals.backgroundScope.launch {
            val mediaManager = MediaQueryManager(requireActivity())
            delay(2000)

// 1. 查询所有相册
            val albums = mediaManager.queryAllAlbums()
            logdNoFile { "albums $albums" }
// 处理相册列表

// 2. 查询全局所有图片+视频
            val allMedia = mediaManager.queryAllImageAndVideo()
            logdNoFile { "allMedia ${allMedia.size}" }
// 3. 查询全局所有视频
            val allVideos = mediaManager.queryAllVideos()
            logdNoFile { "allVideos ${allVideos.size}" }

            val allImages = mediaManager.queryAllImages()
            logdNoFile { "allImages ${allImages.size}" }
// 4. 查询所有音频
             val allAudios = mediaManager.queryAllAudios()
             logdNoFile { "allAudios $allAudios" }
// 处理所有音频

// 3. 查询指定相册的图片（传相册参数）
            val targetAlbum = albums[0] // 从相册列表中取目标相册
            logdNoFile { "targetAlbum $targetAlbum" }
            val albumImages = mediaManager.queryAllImages(targetAlbum)
            logdNoFile { "albumImages $albumImages" }

            val albumVideos = mediaManager.queryAllVideos(targetAlbum)
            logdNoFile { "albumVideos $albumVideos" }

            val albumMedia = mediaManager.queryAllImageAndVideo(targetAlbum)
            logdNoFile { "albumMedia $albumMedia" }
// 处理指定相册的图片

        }
    }

 */
