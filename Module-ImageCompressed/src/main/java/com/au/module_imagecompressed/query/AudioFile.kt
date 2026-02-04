package com.au.module_imagecompressed.query

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore

/**
 * 音频文件实体类
 * @param id 媒体库唯一ID
 * @param name 音频名
 * @param path 文件绝对路径
 * @param artist 演唱者
 * @param album 所属专辑
 * @param duration 时长（毫秒）
 * @param size 文件大小（字节）
 * @param modifyTime 修改时间戳（秒）
 */
data class AudioFile(
    val id: Long,
    val name: String?,
    val mimeType: String?,
    val artist: String?,
    val album: String?,
    val duration: Long,
    val size: Long,
    val modifyTime: Long
)

val AudioFile.contentUri: Uri
    get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)