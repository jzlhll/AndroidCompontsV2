package com.au.module_android.utilsmedia

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import androidx.core.graphics.scale

/**
// 1. 实例化工具类（建议复用，避免重复创建）
val thumbnailUtil = ThumbnailCompatUtil(context)

// 示例尺寸（系统常用尺寸，更容易命中缓存）
val targetSize = Size(640, 480)

// ---------------------- 测试方法1：图片路径生成缩略图 ----------------------
// 示例：公共目录图片路径（实际使用时替换为真实路径）
val publicImagePath = "/storage/emulated/0/DCIM/Camera/test_image.jpg"
val privateImagePath = context.cacheDir.absolutePath + "/private_image.jpg" // 私有目录示例
val imageBitmap1 = thumbnailUtil.createImageThumbnailByPath(publicImagePath, targetSize)
val privateImageBitmap1 = thumbnailUtil.createImageThumbnailByPath(privateImagePath, targetSize)

// ---------------------- 测试方法2：视频路径生成缩略图 ----------------------
// 示例：公共目录视频路径（实际使用时替换为真实路径）
val publicVideoPath = "/storage/emulated/0/DCIM/Camera/test_video.mp4"
val privateVideoPath = context.filesDir.absolutePath + "/private_video.mp4" // 私有目录示例
val videoBitmap2 = thumbnailUtil.createVideoThumbnailByPath(publicVideoPath, targetSize)
val privateVideoBitmap2 = thumbnailUtil.createVideoThumbnailByPath(privateVideoPath, targetSize)

// ---------------------- 测试方法3：Android 10+ ContentUri 加载 ----------------------
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
// 示例：图片 ContentUri（实际使用时替换为真实 MediaStore Uri）
val imageContentUri = Uri.parse("content://media/external/images/media/1001")
val imageBitmap3 = thumbnailUtil.loadThumbnailByContentUriQPlus(imageContentUri, targetSize)

// 示例：视频 ContentUri（实际使用时替换为真实 MediaStore Uri）
val videoContentUri = Uri.parse("content://media/external/video/media/2001")
val videoBitmap3 = thumbnailUtil.loadThumbnailByContentUriQPlus(videoContentUri, targetSize)
}

// ---------------------- 测试方法4：MediaMetadataRetriever 生成视频缩略图 ----------------------
runBlocking { // 协程作用域（实际项目建议用 lifecycleScope/viewModelScope）
// 示例：视频 ContentUri（支持 FileUri/ContentUri，私有/公共文件均可）
val retrieverUri = Uri.parse("content://media/external/video/media/2001")
val privateRetrieverUri = Uri.fromFile(File(context.cacheDir, "private_video.mp4"))
val videoBitmap4 = thumbnailUtil.getVideoThumbnailByRetriever(retrieverUri, targetSize)
val privateVideoBitmap4 = thumbnailUtil.getVideoThumbnailByRetriever(privateRetrieverUri, targetSize)
}

// ---------------------- 测试兼容方法：Android 8+ 通用加载 ----------------------
// 示例：图片 ContentUri（公共媒体文件）
val compatImageUri = Uri.parse("content://media/external/images/media/1001")
val compatImageBitmap = thumbnailUtil.loadThumbnailCompat(compatImageUri, targetSize)

// 示例：视频 ContentUri（公共媒体文件）
val compatVideoUri = Uri.parse("content://media/external/video/media/2001")
val compatVideoBitmap = thumbnailUtil.loadThumbnailCompat(compatVideoUri, targetSize)
}

 * 缩略图兼容工具类（Android 8+）
 * 整合图片/视频缩略图生成、系统缓存查询、低版本兼容逻辑
 * 普通 Class 实现，需通过构造函数传入 Context 实例使用
 */
class ThumbnailCompatUtil(private val context: Context) {
    companion object {
        val LOW_SIZE = Size(240, 320)
        val MID_SIZE = Size(480, 640)
    }

    /**
     * 【方法1】通过文件路径生成图片缩略图（兼容 API 28-）
     * @param filePath 图片文件绝对路径（公共目录/私有目录均可）
     * @param size 缩略图目标尺寸
     * @return 生成的 Bitmap，文件不存在/生成失败返回 null
     * @API 版本要求：
     *  - API 29+：使用 ThumbnailUtils.createImageThumbnail（官方推荐）；
     *  - API 28-：使用 ThumbnailUtils.createThumbnail（已废弃但兼容）；
     * @缓存/生成逻辑：
     *  1. 若 filePath 指向**系统公共媒体目录**（如 DCIM/相册）：
     *     - 优先查询系统为该图片缓存的缩略图；
     *     - 无缓存时触发系统临时生成（生成后写入系统缓存），并返回结果；
     *     - 并非每次创建，仅缓存缺失时生成。
     *  2. 若 filePath 指向**App 私有目录**（cache/files）：
     *     - 无法查询到任何系统缓存（系统不管理私有文件）；
     *     - 每次调用都会临时生成缩略图（仅内存中创建 Bitmap）；
     *     - 生成的缩略图不会写入系统缓存，下次调用仍会重新生成。
     */
    fun createImageThumbnailByPath(filePath: String, size: Size): Bitmap? {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                return null
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // API 29+ 官方方法
                ThumbnailUtils.createImageThumbnail(file, size, null)
            } else {
                // API 28- 兼容方法（已废弃，但无替代方案）
                ThumbnailUtils.createVideoThumbnail(filePath, MediaStore.Images.Thumbnails.MINI_KIND)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 【方法2】通过文件路径生成视频缩略图（兼容 API 28-）
     * @param filePath 视频文件绝对路径（公共目录/私有目录均可）
     * @param size 缩略图目标尺寸
     * @return 生成的 Bitmap，文件不存在/生成失败返回 null
     * @API 版本要求：
     *  - API 29+：使用 ThumbnailUtils.createVideoThumbnail（官方推荐）；
     *  - API 28-：使用 ThumbnailUtils.createThumbnail（已废弃但兼容）；
     * @缓存/生成逻辑：
     *  1. 若 filePath 指向**系统公共媒体目录**（如 DCIM/相册）：
     *     - 优先查询系统为该视频缓存的缩略图；
     *     - 无缓存时触发系统临时生成（生成后写入系统缓存），并返回结果；
     *     - 并非每次创建，仅缓存缺失时生成。
     *  2. 若 filePath 指向**App 私有目录**（cache/files）：
     *     - 无法查询到任何系统缓存（系统不管理私有文件）；
     *     - 每次调用都会临时生成缩略图（仅内存中创建 Bitmap）；
     *     - 生成的缩略图不会写入系统缓存，下次调用仍会重新生成。
     */
    fun createVideoThumbnailByPath(filePath: String, size: Size): Bitmap? {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                return null
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // API 29+ 官方方法
                ThumbnailUtils.createVideoThumbnail(file, size, null)
            } else {
                // API 28- 兼容方法（已废弃，但无替代方案）
                ThumbnailUtils.createVideoThumbnail(filePath, MediaStore.Images.Thumbnails.MINI_KIND)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 【方法3】Android 10+ 原生通过 ContentUri 加载缩略图（系统推荐）
     * @param contentUri 图片/视频的 MediaStore ContentUri（仅支持系统公共媒体文件）
     * @param size 缩略图目标尺寸
     * @return 系统缓存/生成的 Bitmap，失败返回 null
     * @API 版本要求：API 29+（Android 10）
     * @缓存/生成逻辑：
     *  1. 仅支持系统公共媒体文件（ContentUri 由 MediaStore 生成），不支持 App 私有文件；
     *  2. 优先读取系统已缓存的缩略图，不会重复生成；
     *  3. 若系统无缓存，会触发系统生成缩略图（生成后写入系统缓存），并返回生成结果；
     *  4. 仅首次（缓存缺失）会创建缩略图，后续调用直接读取缓存。
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun loadThumbnailByContentUriQPlus(contentUri: Uri, size: Size): Bitmap? {
        return try {
            context.contentResolver.loadThumbnail(contentUri, size, null)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 【方法4】通过 MediaMetadataRetriever 手动生成视频缩略图
     * @param uri 视频文件的 Uri（FileUri/ContentUri 均可，支持公共/私有文件）
     * @param size 缩略图目标尺寸
     * @return 手动生成的 Bitmap，失败返回 null
     * @API 版本要求：无（全版本兼容）
     * @缓存/生成逻辑：
     *  1. 完全绕开系统缓存，**每次调用都会重新生成缩略图**；
     *  2. 优先读取视频内置的缩略图（若有），无则提取视频第一帧并缩放；
     *  3. 无论公共/私有文件，都不会触发系统缓存写入，也不会查询系统缓存；
     *  4. 生成的缩略图仅存在于内存，下次调用需重新生成。
     */
    suspend fun getVideoThumbnailByRetriever(uri: Uri, size: Size): Bitmap? = withContext(Dispatchers.IO) {
        var mediaMetadataRetriever: MediaMetadataRetriever? = null
        return@withContext try {
            mediaMetadataRetriever = MediaMetadataRetriever()
            mediaMetadataRetriever.setDataSource(context, uri)

            // 优先读取视频内置缩略图（无系统缓存逻辑）
            val thumbnailBytes = mediaMetadataRetriever.embeddedPicture
            if (thumbnailBytes != null) {
                return@withContext BitmapFactory.decodeByteArray(thumbnailBytes, 0, thumbnailBytes.size)
            }

            // 无内置缩略图则提取视频帧并缩放（每次都执行）
            val videoWidth = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloat() ?: size.width.toFloat()
            val videoHeight = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloat() ?: size.height.toFloat()
            val widthRatio = size.width / videoWidth
            val heightRatio = size.height / videoHeight
            val ratio = maxOf(widthRatio, heightRatio)

            val frame = mediaMetadataRetriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            frame?.scale((videoWidth * ratio).toInt(), (videoHeight * ratio).toInt())
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            mediaMetadataRetriever?.release()
        }
    }

    /**
     * 【兼容方法】Android 8+ 通用缩略图加载（图片/视频均支持）
     * @param mediaUri 图片/视频的 MediaStore ContentUri（仅支持系统公共媒体文件）
     * @param size 缩略图目标尺寸（建议用系统常用尺寸：320x240/640x480，更容易命中缓存）
     * @return 系统缓存/生成的 Bitmap，失败返回 null
     * @API 版本要求：Android 8+（API 26）
     * @缓存/生成逻辑：
     *  1. 仅支持系统公共媒体文件（ContentUri 由 MediaStore 生成），不支持 App 私有文件；
     *  2. Android 10+：复用 [loadThumbnailByContentUriQPlus] 逻辑（优先读缓存，缓存缺失触发系统生成并写入）；
     *  3. Android 8/9：先查询 MediaStore 缩略图表读取缓存，无缓存则调用 ThumbnailUtils 触发系统生成（生成后写入缓存）；
     *  4. 仅缓存缺失时会创建缩略图，后续调用直接读取系统缓存。
     */
    fun loadThumbnailCompat(mediaUri: Uri, size: Size): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                loadThumbnailByContentUriQPlus(mediaUri, size)
            } else {
                loadThumbnailBelowQ(mediaUri, size)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ======================== 私有辅助方法（内部逻辑，外部无需调用） ========================

    /**
     * Android 8/9 缩略图加载逻辑（查询系统缓存 + 兜底生成）
     */
    private fun loadThumbnailBelowQ(mediaUri: Uri, size: Size): Bitmap? {
        // 解析媒体文件 ID
        val mediaId = getMediaIdFromUri(mediaUri) ?: return null
        // 判断媒体类型（图片/视频）
        val isImage = isImageUri(mediaUri)

        // 优先查询系统缩略图缓存
        val (thumbnailUri) = if (isImage) {
            queryImageThumbnailCache(mediaId, size)
        } else {
            queryVideoThumbnailCache(mediaId, size)
        }

        // 缓存存在则直接读取
        if (thumbnailUri != null) {
            return try {
                context.contentResolver.openInputStream(thumbnailUri)?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream)
                }
            } catch (e: FileNotFoundException) {
                null // 缓存文件损坏，走兜底逻辑
            }
        }

        // 缓存缺失，触发系统生成（生成后写入缓存）
        val mediaPath = getMediaPathFromUri(mediaUri) ?: return null
        return if (isImage) {
            createImageThumbnailByPath(mediaPath, size)
        } else {
            createVideoThumbnailByPath(mediaPath, size)
        }
    }

    /**
     * 从 MediaStore ContentUri 解析媒体文件 ID
     */
    private fun getMediaIdFromUri(uri: Uri): String? {
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns._ID),
            null,
            null,
            null
        )
        return cursor?.use {
            if (it.moveToFirst()) {
                it.getString(it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
            } else {
                null
            }
        }
    }

    /**
     * 判断 Uri 是否为图片类型
     */
    private fun isImageUri(uri: Uri): Boolean {
        val uriStr = uri.toString()
        return uriStr.contains("images") || uriStr.contains(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.path!!)
    }

    /**
     * 查询图片的系统缩略图缓存
     */
    private fun queryImageThumbnailCache(mediaId: String, size: Size): Pair<Uri?, String?> {
        val projection = arrayOf(
            MediaStore.Images.Thumbnails._ID,
            MediaStore.Images.Thumbnails.DATA,
            MediaStore.Images.Thumbnails.IMAGE_ID
        )
        val selection = "${MediaStore.Images.Thumbnails.IMAGE_ID} = ?"
        val selectionArgs = arrayOf(mediaId)

        val cursor = context.contentResolver.query(
            MediaStore.Images.Thumbnails.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )

        return cursor?.use {
            if (it.moveToFirst()) {
                val path = it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Thumbnails.DATA))
                val thumbId = it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Thumbnails._ID))
                val thumbUri = Uri.withAppendedPath(
                    MediaStore.Images.Thumbnails.EXTERNAL_CONTENT_URI,
                    thumbId
                )
                Pair(thumbUri, path)
            } else {
                Pair(null, null)
            }
        } ?: Pair(null, null)
    }

    /**
     * 查询视频的系统缩略图缓存
     */
    private fun queryVideoThumbnailCache(mediaId: String, size: Size): Pair<Uri?, String?> {
        val projection = arrayOf(
            MediaStore.Video.Thumbnails._ID,
            MediaStore.Video.Thumbnails.DATA,
            MediaStore.Video.Thumbnails.VIDEO_ID
        )
        val selection = "${MediaStore.Video.Thumbnails.VIDEO_ID} = ?"
        val selectionArgs = arrayOf(mediaId)

        val cursor = context.contentResolver.query(
            MediaStore.Video.Thumbnails.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )

        return cursor?.use {
            if (it.moveToFirst()) {
                val path = it.getString(it.getColumnIndexOrThrow(MediaStore.Video.Thumbnails.DATA))
                val thumbId = it.getString(it.getColumnIndexOrThrow(MediaStore.Video.Thumbnails._ID))
                val thumbUri = Uri.withAppendedPath(
                    MediaStore.Video.Thumbnails.EXTERNAL_CONTENT_URI,
                    thumbId
                )
                Pair(thumbUri, path)
            } else {
                Pair(null, null)
            }
        } ?: Pair(null, null)
    }

    /**
     * 从 MediaStore Uri 获取文件路径（Android 8/9 需 READ_EXTERNAL_STORAGE 权限）
     */
    private fun getMediaPathFromUri(uri: Uri): String? {
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DATA),
            null,
            null,
            null
        )
        return cursor?.use {
            if (it.moveToFirst()) {
                it.getString(it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA))
            } else {
                null
            }
        }
    }
}