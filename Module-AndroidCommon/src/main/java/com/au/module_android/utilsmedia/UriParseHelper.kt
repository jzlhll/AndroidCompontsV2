package com.au.module_android.utilsmedia

import android.content.ContentResolver
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.webkit.MimeTypeMap
import androidx.core.net.toFile
import androidx.core.net.toUri
import com.au.module_android.Globals
import com.au.module_android.utils.ignoreError
import kotlinx.coroutines.delay
import java.io.File

internal class UriParseHelper {
    suspend fun parseSuspend(cr: ContentResolver, uri: Uri): UriParsedInfo {
        delay(0)
        return parse(cr, uri)
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

    fun parse(cr: ContentResolver, uri: Uri) : UriParsedInfo{
//        logdNoFile { "parse uri: $uri" }
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
        return if (file != null) {
            parse(file, uri)
        } else if (isFileScheme(uri)) {
            parse(uri.toFile(), uri)
        } else {
            parseAsContent(cr, uri)
        }
    }

    ///////////////必须先调用 parse
    fun parse(file: File, fileItsUri: Uri?=null) : UriParsedInfo{
        val extension = file.extension.lowercase()
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
        val fileLength = file.length()
        val videoDuration = VideoDurationHelper().getDurationNormally(file.absolutePath, mimeType)
        val lastModified = file.lastModified()

        return UriParsedInfo(fileItsUri ?: file.toUri(),
            file.name,
            fileLength,
            extension,
            mimeType,
            file.absolutePath,
            null,
            videoDuration,
            isFile = true,
            lastModified = lastModified) //todo 是否考虑 fileDescriptor
    }

    private fun parseAsContent(cr: ContentResolver, uri: Uri) : UriParsedInfo {
        var mimeType = ""
        var relativePath:String? = null
        var fullPath:String? = null
        var fileLength = 0L
        var name = ""
        var videoDuration:Long? = null
        var lastModified:Long? = null
        var size: Size? = null
        ignoreError {
            val projection = if (uri.authority == MediaStore.AUTHORITY) {
                arrayOf(
                    MediaStore.MediaColumns.MIME_TYPE,
                    MediaStore.MediaColumns.DATA,
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.DURATION,
                    MediaStore.MediaColumns.DATE_MODIFIED,
                    MediaStore.MediaColumns.DATE_ADDED,
                    MediaStore.MediaColumns.WIDTH,
                    MediaStore.MediaColumns.HEIGHT
                )
            } else {
                null
            }
            val cursor = ignoreError {
                cr.query(uri, projection, null, null, null)
            } ?: if (projection == null) {
                null
            } else {
                ignoreError {
                    cr.query(uri, null, null, null, null)
                }
            }
            cursor?.use { cursor->
                if (cursor.moveToFirst()) {
                    //解析 mimeType
                    val mimeTypeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                    if (mimeTypeIndex != -1) {
                        mimeType = cursor.getString(mimeTypeIndex)
                    }

                    //解析 relative path / full path
                    val dataIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    fullPath = if (dataIndex == -1) null else cursor.getString(dataIndex)

                    val relativePathIndex = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                    relativePath = if (relativePathIndex == -1) null else cursor.getString(relativePathIndex)

                    //解析 length
                    val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    fileLength = if (sizeIndex == -1) 0L else cursor.getLong(sizeIndex)

                    //解析 name
                    val displayNameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    name = if (displayNameIndex == -1) "" else cursor.getString(displayNameIndex)

                    //解析 video duration
                    val durationIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DURATION)
                    videoDuration = if (durationIndex == -1) 0L else cursor.getLong(durationIndex)

                    //解析最后修改时间
                    val dateModifiedIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                    lastModified = if (dateModifiedIndex == -1) null else cursor.getLong(dateModifiedIndex)

                    if (lastModified == null || lastModified == 0L) {
                        //解析创建时间
                        val dateAddedIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                        lastModified = if (dateAddedIndex == -1) null else cursor.getLong(dateAddedIndex)
                    }

                    //解析图片像素尺寸
                    val widthIndex = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                    val heightIndex = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                    if (mimeType.startsWith("image/") && widthIndex != -1 && heightIndex != -1) {
                        val width = cursor.getInt(widthIndex)
                        val height = cursor.getInt(heightIndex)
                        if (width > 0 && height > 0) {
                            size = Size(width, height)
                        }
                    }
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

        val r = UriParsedInfo(uri,
            name,
            fileLength,
            extension,
            mimeType,
            fullPath,
            relativePath,
            videoDuration,
            isFile = false,
            lastModified = lastModified,
            size = size)

//        logdNoFile { "parseAsContent parsed Info: $r" }
        return r
    }

}
