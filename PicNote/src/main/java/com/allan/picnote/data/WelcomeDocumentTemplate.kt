package com.allan.picnote.data

import kotlinx.serialization.Serializable

const val WelcomeDocumentId = "welcome"

/** 描述内置教程的本地化内容和随包图片信息。 */
@Serializable
data class WelcomeDocumentTemplate(
    val title: String,
    val introduction: String,
    val imageFileName: String,
    val imageWidth: Int,
    val imageHeight: Int,
    val imageSha256: String,
    val instruction: String,
    val todoTitle: String,
)
