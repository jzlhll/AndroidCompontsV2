package com.au.module_imagecompressed.query

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore

/**
 * 图片/视频媒体文件实体类
 * @param id 媒体库唯一ID
 * @param name 文件名
 * @param mimeType 文件MIME类型（image, video）
 * @param size 文件大小（字节）
 * @param duration 时长（视频：毫秒；图片：0）
 * @param modifyTime 修改时间戳（秒），用于排序
 * @param bucketId 所属相册ID
 */
data class MediaFile(
    val id: Long,
    val name: String?,
    val mimeType: String?,
    val size: Long,
    val duration: Long,
    val modifyTime: Long,
    val bucketId: Long
)

val MediaFile.contentUri: Uri
    get() {
        val baseUri = if (mimeType?.startsWith("video") == true) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        return ContentUris.withAppendedId(baseUri, id)
    }