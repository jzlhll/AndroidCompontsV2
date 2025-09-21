package com.au.module_android.utilsmedia

import android.content.ContentResolver
import android.net.Uri
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.net.toFile
import com.au.module_android.utils.ignoreError
import com.au.module_android.utils.logdNoFile
import kotlinx.coroutines.delay
import java.io.File

class UriParserUtil(private val uri: Uri) {
    lateinit var parsedInfo : UriParsedInfo

    fun isFullPath() : Boolean = parsedInfo.fullPath != null

    fun isUriHeic() : Boolean{
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(parsedInfo.mimeType)
        return extension?.lowercase() == "heic"
    }

    fun isUriVideo(): Boolean {
        return parsedInfo.mimeType.startsWith("video/")
    }

    fun isUriImage(): Boolean {
        return parsedInfo.mimeType.startsWith("image/")
    }

    suspend fun parseSuspend(cr: ContentResolver): UriParsedInfo {
        delay(0)
        return parse(cr)
    }

    fun parse(cr: ContentResolver) : UriParsedInfo{
        logdNoFile { "parse uri: $uri" }
        if (isFileScheme()) {
            parseAsFile(uri.toFile())
        } else {
            parseAsContent(cr)
        }
        return parsedInfo
    }

    private fun parseAsFile(file: File) : Boolean{
        val extension = file.extension.lowercase()
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
        val fileLength = file.length()
        val videoDuration = MediaHelper().getDurationNormally(file.absolutePath, mimeType)

        parsedInfo = UriParsedInfo(uri,
            file.name,
            fileLength,
            extension,
            mimeType,
            file.absolutePath,
            null,
            videoDuration)
        logdNoFile { "parseAsFile parsed Info: $parsedInfo" } //todo 是否考虑fileDescriptor
        return parsedInfo.fileLength > 0
    }

    private fun parseAsContent(cr: ContentResolver) {
        var mimeType = ""
        var relativePath:String? = null
        var fullPath:String? = null
        var fileLength = 0L
        var name = ""
        var videoDuration:Long? = null
        ignoreError {
            cr.query(uri, null, null, null, null)?.use { cursor->
                if (cursor.moveToFirst()) {
                    //解析mimeType
                    val mimeTypeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                    if (mimeTypeIndex != -1) {
                        mimeType = cursor.getString(mimeTypeIndex)
                    }

                    //解析relative path / full path
                    val relativePathIndex = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                    relativePath = if (relativePathIndex == -1) null else cursor.getString(relativePathIndex)
                    if (relativePath == null) {
                        val dataIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                        fullPath = if (dataIndex == -1) null else cursor.getString(dataIndex)
                    }

                    //解析length
                    val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    fileLength = if (sizeIndex == -1) 0L else cursor.getLong(sizeIndex)

                    //解析name
                    val displayNameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    name = if (displayNameIndex == -1) "" else cursor.getString(displayNameIndex)

                    //解析video duration
                    val durationIndex = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                    videoDuration = if (durationIndex == -1) 0L else cursor.getLong(durationIndex)
                }
            }
        }

        val extension = if (mimeType.isNotEmpty()) {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: ""
        } else {
            ""
        }

        if (name.isEmpty()) {
            val n = fullPath ?: relativePath
            if (n == null) {
                val uriStr = uri.toString()
                val last = uriStr.lastIndexOf("/")
                name = uriStr.substring(last + 1)
            } else {
                name = n.substring(n.lastIndexOf("/") + 1)
            }
        }

        parsedInfo = UriParsedInfo(uri,
            name,
            fileLength,
            extension,
            mimeType,
            fullPath,
            relativePath,
            videoDuration)

        logdNoFile { "parseAsContent parsed Info: $parsedInfo" }
    }

    fun isFileScheme() = uri.scheme == ContentResolver.SCHEME_FILE

    fun isContentScheme() = uri.scheme == ContentResolver.SCHEME_CONTENT

    /*

            if (name.isNullOrEmpty()) {
            val namePart = "" + System.currentTimeMillis() + "_" + (Math.random() * 100).toInt()
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)?.lowercase()
            if (extension != null && mimeType.startsWith("video")) {
                return mimeType to "video_$namePart.$extension"
            }
            if (extension != null && mimeType.startsWith("image")) {
                return mimeType to "pic_$namePart.$extension"
            }
            return mimeType to "media_$namePart"
        }
        return mimeType to name
     */
}