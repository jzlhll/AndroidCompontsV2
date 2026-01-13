package com.allan.androidlearning.pictureselector

import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import com.au.module_android.utilsmedia.UriParsedInfo
import com.au.module_imagecompressed.PickUriWrap
import com.luck.picture.lib.entity.LocalMedia
import kotlin.text.ifEmpty
import kotlin.text.substringAfterLast

fun LocalMedia.toLog(): String {
    val sb = kotlin.text.StringBuilder()
    sb.append("LocalMedia Info:\n")
    
    // file to ID
//    sb.append("id: ").append(this.id).append("\n")
    
    // original path
    if(this.path != null) sb.append("path: ").append(this.path).append("\n")
    
    // The real path，But you can't get access from AndroidQ
    if(this.realPath != null) sb.append("realPath: ").append(this.realPath).append(", ")
    
    // original path
    if(this.originalPath != null) sb.append("originalPath: ").append(this.originalPath).append(", ")
    
    // compress path
    if(this.compressPath != null) sb.append("compressPath: ").append(this.compressPath).append(", ")
    // cut path
    if(this.cutPath != null) sb.append("cutPath: ").append(this.cutPath).append(", ")
    // app sandbox path
    if (this.sandboxPath != null && this.sandboxPath == this.cutPath) {
        sb.append("sandboxPath=[cutPath], ")
    } else if (this.sandboxPath != null && this.sandboxPath == this.compressPath) {
        sb.append("sandboxPath=[compressPath], ")
    } else {
        if(this.sandboxPath != null) sb.append("sandboxPath: ").append(this.sandboxPath).append(", ")
    }
    
    // watermark path
//    if(this.watermarkPath != null) sb.append("watermarkPath: ").append(this.watermarkPath).append(", ")
    
    // video thumbnail path
    if(this.videoThumbnailPath != null) sb.append("videoThumbnailPath: ").append(this.videoThumbnailPath).append(", ")

    sb.append("\n")
    
    // video duration
    sb.append("duration: ").append(this.duration).append("\n")
    
    // The media resource type
    sb.append("mimeType: ").append(this.mimeType).append("\n")
    // file size
    sb.append("size: ").append(this.size).append("\n")

    // file name
    sb.append("fileName: ").append(this.fileName).append("\n\n")
    
    // Parent Folder Name
//    sb.append("parentFolderName: ").append(this.parentFolderName).append("\n")

    return sb.toString()
}

fun LocalMedia.uri() : Uri = this.path.toUri()

fun LocalMedia.toUriParsedInfo() : UriParsedInfo{
    val fileNameExTension = this.fileName.substringAfterLast(".")
    val extension = fileNameExTension.ifEmpty {
        if (this.mimeType != null) {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(this.mimeType) ?: ""
        } else ""
    }

    return UriParsedInfo(
        uri = this.uri(),
        name = this.fileName,
        fileLength = this.size,
        extension = extension,
        mimeType = this.mimeType ?: "",
        fullPath = this.realPath,
//        relativePath = this.originalPath,
        videoDuration = this.duration
    )
}

fun LocalMedia.toPickUriWrap(totalNum:Int) : PickUriWrap {
    val info = toUriParsedInfo()
    return PickUriWrap(
        info,
        totalNum,
        info.isUriImage(),
        false
    )
}
