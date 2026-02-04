package com.au.module_imagecompressed.query

/**
 * 相册实体类（对应MediaStore的相册桶）
 * @param bucketId 相册唯一标识（必传，MediaStore的BUCKET_ID）
 * @param name 相册名称
 * @param count 相册内媒体文件数量
 */
data class Album(
    val bucketId: Long = -1,
    val name: String?,
    val coverUri: android.net.Uri?,
    var count: Int = 0
)
