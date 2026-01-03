package com.au.module_android.utilsmedia

import android.content.ContentResolver
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.text.TextUtils
import android.util.Log
import com.au.module_android.Globals
import com.au.module_android.utilsfile.FileIOUtils
import java.io.File
import java.io.FileNotFoundException

fun File.myParse() = UriParseHelper(this).parseFile()
fun Uri.myParse(context: Context = Globals.app) = UriParseHelper(this).parse(context.contentResolver)
fun Uri.myParse(cr: ContentResolver) = UriParseHelper(this).parse(cr)
suspend fun Uri.myParseSuspend(context: Context = Globals.app) = UriParseHelper(this).parseSuspend(context.contentResolver)
suspend fun Uri.myParseSuspend(cr: ContentResolver) = UriParseHelper(this).parseSuspend(cr)

fun isUrlHasImage(url: String): Boolean {
    val lowUrl = url.lowercase()
    return (lowUrl.endsWith(".jpg")
            || lowUrl.endsWith(".jpeg")
            || lowUrl.endsWith(".png")
            || lowUrl.endsWith(".heic"))
}

fun isHasHttp(path: String): Boolean {
    if (TextUtils.isEmpty(path)) {
        return false
    }
    return path.startsWith("http") || path.startsWith("https")
}

fun isFileScheme(uri: Uri) = uri.scheme == ContentResolver.SCHEME_FILE

fun isContentScheme(uri: Uri) = uri.scheme == ContentResolver.SCHEME_CONTENT

/**
 * 基本上都已经拷贝过的图才能如此操作。
 */
fun isPicCanCompress(path:String) = isUrlHasImage(path) && !isHasHttp(path)

/**
 * 获取Uri的文件大小
 */
fun Uri.length(cr: ContentResolver, schemeForce:String? = null) : Long {
    var resultLength = -1L
    when (schemeForce ?: scheme) {
        ContentResolver.SCHEME_FILE -> {
            // Try to get content length from content scheme uri or file scheme uri
            var fileDescriptor: ParcelFileDescriptor? = null
            try {
                fileDescriptor = cr.openFileDescriptor(this, "r")
                    ?: throw Exception("Content provider recently crashed")
                resultLength = fileDescriptor.statSize
            } catch (e: Exception) {
                Log.d("UrlUtil", e.message ?: e.javaClass.simpleName)
                resultLength = -1L
            } finally {
                fileDescriptor?.close()
            }
        }

        ContentResolver.SCHEME_CONTENT -> {
            // Try to get content length from the content provider column OpenableColumns.SIZE
            // which is recommended to implement by all the content providers
            var cursor: Cursor? = null
            try {
                cursor = cr.query(
                    this,
                    arrayOf(OpenableColumns.SIZE),
                    null,
                    null,
                    null
                ) ?: throw Exception("Content provider returned null or crashed")
                val sizeColumnIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeColumnIndex != -1 && cursor.count > 0) {
                    cursor.moveToFirst()
                    resultLength = cursor.getLong(sizeColumnIndex)
                } else {
                    resultLength = -1L
                }
            } catch (e: Exception) {
                Log.d("UrlUtil", e.message ?: e.javaClass.simpleName)
                resultLength = -1L
            } finally {
                cursor?.close()
            }

            if (resultLength == -1L) {
                resultLength = this.length(cr, ContentResolver.SCHEME_FILE)
            }

            if (resultLength == -1L) {
                cr.openInputStream(this)?.use {
                    resultLength = it.available().toLong()
                }
            }
        }
        ContentResolver.SCHEME_ANDROID_RESOURCE -> {
            // Try to get content length from content scheme uri, file scheme uri or android resource scheme uri
            var assetFileDescriptor: AssetFileDescriptor? = null
            try {
                assetFileDescriptor = cr.openAssetFileDescriptor(this, "r")
                    ?: throw Exception("Content provider recently crashed")
                resultLength = assetFileDescriptor.length
            } catch (e: Exception) {
                Log.d("UrlUtil", e.message ?: e.javaClass.simpleName)
                resultLength = -1L
            } finally {
                assetFileDescriptor?.close()
            }
        }
    }

    return resultLength
}

/**
 * 判断 Uri 是否来源于当前应用
 * - 对 `content://` 类型的 Uri，验证其 ContentProvider 的包名
 * - 对 `file://` 类型的 Uri，验证文件路径是否位于应用私有目录
 */
fun Uri.isFromMyApp(context: Context): Boolean {
    val packageName = context.packageName
    return when (scheme) {
        ContentResolver.SCHEME_CONTENT -> {
            // 检查 ContentProvider 的包名
            val auth = authority ?: return false
            try {
                val providerInfo = context.packageManager.resolveContentProvider(auth, 0)
                providerInfo?.packageName == packageName
            } catch (_: Exception) {
                false
            }
        }
        ContentResolver.SCHEME_FILE -> {
            // 检查文件路径是否在应用私有目录中
            val path = path ?: return false
            val appDirs = listOfNotNull(
                context.filesDir?.absolutePath,
                context.cacheDir?.absolutePath,
                context.externalCacheDir?.absolutePath
            )
            appDirs.any { path.startsWith(it) }
        }
        else -> false
    }
}

fun Uri.copyToCache(name:String? = null) : File? {
    return try {
        Globals.app.contentResolver.openInputStream(this)?.use { inputStream ->
            val file = File(Globals.app.cacheDir, name ?: System.currentTimeMillis().toString())
            FileIOUtils.writeFileFromInputStream(file, inputStream, false)
            file
        }
    } catch (e: FileNotFoundException) {
        e.printStackTrace()
        null
    }
}
