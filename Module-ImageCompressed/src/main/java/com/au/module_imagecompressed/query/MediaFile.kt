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
 * @param width 媒体宽度（像素）
 * @param height 媒体高度（像素）
 * @param orientation 媒体旋转角度
 * @param isCamera 是否来自系统相机目录
 * @param isScreenshot 是否来自系统截图目录
 *
 * size、duration、modifyTime、width、height 无法解析时为 0，
 * isCamera、isScreenshot 无法解析时为 false，其余可选字段无法解析时为 null。
 */
data class MediaFile(
    val id: Long,
    val name: String?,
    val mimeType: String?,
    val size: Long,
    val duration: Long,
    val modifyTime: Long,
    val bucketId: Long?,
    val width: Int = 0,
    val height: Int = 0,
    val orientation: Int? = null,
    val isCamera: Boolean = false,
    val isScreenshot: Boolean = false
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