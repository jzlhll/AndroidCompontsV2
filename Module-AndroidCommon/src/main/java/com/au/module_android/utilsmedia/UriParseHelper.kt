package com.au.module_android.utilsmedia

import android.content.ContentResolver
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.net.toFile
import com.au.module_android.Globals
import com.au.module_android.log.logdNoFile
import com.au.module_android.utils.ignoreError
import kotlinx.coroutines.delay
import java.io.File

class UriParseHelper {
    private lateinit var uri: Uri
    private lateinit var file: File

    constructor(uri: Uri) {
        this.uri = uri
    }

    constructor(file: File) {
        this.file = file
    }

    private lateinit var parsedInfo : UriParsedInfo

    suspend fun parseSuspend(cr: ContentResolver): UriParsedInfo {
        delay(0)
        return parse(cr)
    }

    /**
     * 判断是否是rootUri
     */
    private fun isRootUri(uri: Uri?): Boolean {
        if (uri == null) {
            return false
        }
        if (ContentResolver.SCHEME_CONTENT != uri.scheme) {
            return false
        }
        val paths = uri.pathSegments
        return paths != null && "root" == paths[0]
    }

    fun parse(cr: ContentResolver) : UriParsedInfo{
        logdNoFile { "parse uri: $uri" }
        val path = uri.path

        var file:File? = null
        if (path != null) {
            if (path.startsWith("/files_path/")) {
                file = File(
                    Globals.app.filesDir.absolutePath
                            + path.replace("/files_path/", "/")
                )
            } else if (path.startsWith("/cache_path/")) {
                file = File(
                    Globals.app.cacheDir.absolutePath
                            + path.replace("/cache_path/", "/")
                )
            } else if (path.startsWith("/external_files_path/")) {
                file = File(
                    Globals.app.getExternalFilesDir(null)?.absolutePath
                            + path.replace("/external_files_path/", "/")
                )
            } else if (path.startsWith("/external_cache_path/")) {
                file = File(
                    Globals.app.externalCacheDir?.absolutePath
                            + path.replace("/external_cache_path/", "/")
                )
            }
            if (file != null && file.exists()) {
                Log.d("UriUtil", "$uri -> $path")
            }
        }

        if (file == null && isRootUri(uri)) {
            val pathArr = uri.toString().split("root".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (pathArr.size > 1) {
                file = File(pathArr[1])
            }
        }

//        if (file == null && "com.huawei.hidisk.fileprovider" == uri.authority) {
//            val path = uri.path
//            if (!TextUtils.isEmpty(path)) {
//                file = File(path!!.replace("/root", ""))
//            }
//        }
        if (file != null) {
            parseAsFile(file)
        } else if (isFileScheme(uri)) {
            parseAsFile(uri.toFile())
        } else {
            parseAsContent(cr)
        }
        return parsedInfo
    }

    fun parseFile(): UriParsedInfo {
        parseAsFile(file)
        return parsedInfo
    }

    ///////////////必须先调用parse
    private fun parseAsFile(file: File) : Boolean{
        val extension = file.extension.lowercase()
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
        val fileLength = file.length()
        val videoDuration = VideoDurationHelper().getDurationNormally(file.absolutePath, mimeType)

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
                    val dataIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    fullPath = if (dataIndex == -1) null else cursor.getString(dataIndex)

                    val relativePathIndex = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                    relativePath = if (relativePathIndex == -1) null else cursor.getString(relativePathIndex)

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

}