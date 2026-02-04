package com.au.module_imagecompressed.query

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 所有查询方法均在IO线程执行，推荐协程调用
 */
class MediaQueryManager(private val context: Context) {
    private val contentResolver by lazy { context.contentResolver }

    // ==================== 1. 查询所有相册列表 ====================
    suspend fun queryAllAlbums(): List<Album> = withContext(Dispatchers.IO) {
        val albumMap = mutableMapOf<Long, Album>()

        val projection = arrayOf(
            FileColumns.BUCKET_ID,
            FileColumns.BUCKET_DISPLAY_NAME,
            FileColumns._ID,              // 用于生成封面 Uri
            FileColumns.MEDIA_TYPE,       // 用于区分 Uri 类型
            FileColumns.DATE_MODIFIED
        )

        val selection = "${FileColumns.MEDIA_TYPE} IN (?, ?)"
        val selectionArgs = arrayOf(
            FileColumns.MEDIA_TYPE_IMAGE.toString(),
            FileColumns.MEDIA_TYPE_VIDEO.toString()
        )

        // 按时间逆序，这样 map 中记录的第一个就是该相册最新的图，适合做封面
        val sortOrder = "${FileColumns.DATE_MODIFIED} DESC"

        contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(FileColumns._ID)
            val typeIdx = c.getColumnIndexOrThrow(FileColumns.MEDIA_TYPE)
            val bucketIdIdx = c.getColumnIndexOrThrow(FileColumns.BUCKET_ID)
            val nameIdx = c.getColumnIndexOrThrow(FileColumns.BUCKET_DISPLAY_NAME)

            while (c.moveToNext()) {
                val bucketId = c.getLong(bucketIdIdx)
                val mediaId = c.getLong(idIdx)
                val mediaType = c.getInt(typeIdx)

                val album = albumMap[bucketId]
                if (album == null) {
                    // 生成封面 Uri
                    val baseUri = if (mediaType == FileColumns.MEDIA_TYPE_VIDEO)
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

                    albumMap[bucketId] = Album(
                        bucketId = bucketId,
                        name = c.getString(nameIdx) ?: "Others",
                        coverUri = ContentUris.withAppendedId(baseUri, mediaId),
                        count = 1,
                    )
                } else {
                    album.count++ // 已经在 Map 里了，累加计数
                }
            }
        }
        return@withContext albumMap.values.toList().sortedByDescending { it.count }
    }

    // ==================== 2. 查询所有图片+视频（支持指定相册，时间逆序）- 仍用Files通用Uri ====================
    suspend fun queryAllImageAndVideo(album: Album? = null): List<MediaFile> = withContext(Dispatchers.IO) {
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

        val cursor: Cursor? = contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selectionBuilder.toString(),
            selectionArgs.toTypedArray(),
            "${FileColumns.DATE_MODIFIED} DESC"
        )
        // 解析Cursor为MediaFile
        cursor?.use { mediaList.addAll(parseMediaCursor(it, projection)) }
        return@withContext mediaList
    }

    // ==================== 3. 查询所有图片（支持指定相册，时间逆序）- 改用MediaStore.Images专属Uri ====================
    suspend fun queryAllImages(album: Album? = null): List<MediaFile> = withContext(Dispatchers.IO) {
        val contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.BUCKET_ID
        )
        val selectionBuilder = StringBuilder()
        val selectionArgs = mutableListOf<String>()

        // 仅筛选指定相册，无需筛选媒体类型（专属Uri已限定）
        addAlbumFilter(selectionBuilder, selectionArgs, album)

        val cursor: Cursor? = contentResolver.query(
            contentUri,
            projection,
            if (selectionBuilder.isNotEmpty()) selectionBuilder.toString() else null,
            if (selectionArgs.isNotEmpty()) selectionArgs.toTypedArray() else null,
            "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
        )
        // 解析Cursor为MediaFile
        val mediaList = mutableListOf<MediaFile>()
        cursor?.use {
            mediaList.addAll(parseMediaCursor(it, projection))
        }
        return@withContext mediaList
    }

    // ==================== 4. 查询所有视频（支持指定相册，时间逆序）- 改用MediaStore.Video专属Uri ====================
    suspend fun queryAllVideos(album: Album? = null): List<MediaFile> = withContext(Dispatchers.IO) {
        val contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

        val mediaList = mutableListOf<MediaFile>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Video.Media.DURATION,
        )
        val selectionBuilder = StringBuilder()
        val selectionArgs = mutableListOf<String>()

        // 仅筛选指定相册，无需筛选媒体类型（专属Uri已限定）
        addAlbumFilter(selectionBuilder, selectionArgs, album)

        val cursor: Cursor? = contentResolver.query(
            contentUri,
            projection,
            if (selectionBuilder.isNotEmpty()) selectionBuilder.toString() else null,
            if (selectionArgs.isNotEmpty()) selectionArgs.toTypedArray() else null,
            "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
        )
        // 解析Cursor为MediaFile
        cursor?.use {
            mediaList.addAll(parseMediaCursor(it, projection))
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

    /**
     * 公共方法：解析Cursor为MediaFile列表（全量安全取值，适配所有媒体查询）
     */
    private fun parseMediaCursor(cursor: Cursor, projection: Array<String>): List<MediaFile> {
        val mediaList = mutableListOf<MediaFile>()
        // 获取所有列索引（全量使用getColumnIndex）
        val idIndex = cursor.getColumnIndex(projection[0])
        val nameIndex = if (projection.size > 1) cursor.getColumnIndex(projection[1]) else -1
        val mimeTypeIndex = if (projection.size > 2) cursor.getColumnIndex(projection[2]) else -1
        val sizeIndex = if (projection.size > 3) cursor.getColumnIndex(projection[3]) else -1
        val modifyTimeIndex = if (projection.size > 4) cursor.getColumnIndex(projection[4]) else -1
        val bucketIdIndex = if (projection.size > 5) cursor.getColumnIndex(projection[5]) else -1
        val durationIndex = if (projection.size > 6) cursor.getColumnIndex(projection[6]) else -1

        while (cursor.moveToNext()) {
            // 索引≥0才取值，否则给默认值，彻底避免崩溃
            val id = if (idIndex >= 0) cursor.getLong(idIndex) else 0L
            val rawName = if (nameIndex >= 0) cursor.getString(nameIndex) else null
            val name = rawName?.substringBeforeLast(".")
            val mimeType = if (mimeTypeIndex >= 0) cursor.getString(mimeTypeIndex) else null
            val size = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L
            val duration = if (durationIndex >= 0) cursor.getLong(durationIndex) else 0L
            val modifyTime = if (modifyTimeIndex >= 0) cursor.getLong(modifyTimeIndex) else 0L
            val bucketId = if (bucketIdIndex >= 0) cursor.getLong(bucketIdIndex) else -1

            mediaList.add(
                MediaFile(
                    id = id,
                    name = name,
                    mimeType = mimeType,
                    size = size,
                    duration = duration,
                    modifyTime = modifyTime,
                    bucketId = bucketId
                )
            )
        }
        return mediaList
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