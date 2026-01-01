package com.au.module_android.utilsmedia

import com.au.module_android.Globals
import com.au.module_android.log.logt
import android.content.ContentResolver
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.text.TextUtils
import android.util.Log
import androidx.annotation.WorkerThread
import java.io.*

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
 * 将Uri识别，拷贝到本地cache；如果param有传参，则会进行转换拷贝。
 *
 * 不管是不是图片是不是进行转换，都会拷贝（file型的uri除外）。
 *
 * 本函数会耗时。自行放到scope中运行。
 */
@WorkerThread
fun Uri.copyToCacheConvert(cr:ContentResolver,
                           param:String? = URI_COPY_PARAM_HEIC_TO_JPG,
                           subCacheDir:String,
                           copyFilePrefix:String = "copy_",
                           size:LongArray? = null) : Uri{
    val file = this.copyToCacheFile(cr, param, subCacheDir, copyFilePrefix, size)
    return Uri.fromFile(file)
}

/**
 * 经过研究，android对于content uri想要使用File最好的办法，就是拷贝到自己的目录下。
 * 才是最保险的，而且不需要考虑权限问题。
 *
 * 不管是不是图片是不是进行转换，都会拷贝（file型的uri除外）。
 *
 * 自行考虑放到Scope中运行。可能会耗时比较多，比如拷贝视频。
 *
 * @param param 参考URI_COPY_PARAM_XXX
 *
 * @return 不太可能是空。
 */
@WorkerThread
fun Uri.copyToCacheFile(cr: ContentResolver,
                        param:String? = null,
                        subCacheDir:String,
                        copyFilePrefix:String = "copy_",
                        size:LongArray? = null): File {
    if (this.scheme == ContentResolver.SCHEME_FILE) {
        return copyToCacheFileSchemeFile(size)!!
    } else if (this.scheme == ContentResolver.SCHEME_CONTENT) {
        return copyToCacheFileSchemeContent(cr, param, subCacheDir, copyFilePrefix, size)
    }
    throw IllegalArgumentException()
}

private fun Uri.copyToCacheFileSchemeContent(cr: ContentResolver,
                                             param:String? = null,
                                             subCacheDir:String,
                                             copyFilePrefix:String = "copy_",
                                             size:LongArray? = null) : File {
    val parsedInfo = UriParseHelper(this).parse(cr)
    val extension = parsedInfo.extension

    val cacheDir = Globals.goodCacheDir
    val isSourceHeic = extension == "heic"
    logt(tag = "picker") { "$this $param, extension: $extension"}
    val cvtExtension = targetFileExtensionName(extension, param, isSourceHeic)

    val displayName = copyFilePrefix + System.currentTimeMillis() + "_" + (Math.random() * 1000).toInt().toString() + "." + cvtExtension
    val subDirFile = File(cacheDir.absolutePath + "/$subCacheDir")
    if (!subDirFile.exists()) {
        subDirFile.mkdirs()
    }
    val targetFile = File(cacheDir.absolutePath + "/$subCacheDir", displayName)
    copyFromCr(cr, targetFile, param, extension, isSourceHeic, size)
    return targetFile
}

private fun targetFileExtensionName(extension: String?, param: String?, isSourceHeic: Boolean): String? {
    var cvtExtension = extension
    if (extension != null && isSupportConvertImage(extension)) {
        when (param) {
            URI_COPY_PARAM_ANY_TO_JPG -> {
                cvtExtension = "jpg"
            }

            URI_COPY_PARAM_HEIC_TO_PNG -> {
                if (isSourceHeic) {
                    cvtExtension = "png"
                }
            }

            URI_COPY_PARAM_HEIC_TO_JPG -> {
                if (isSourceHeic) {
                    cvtExtension = "jpg"
                }
            }
        }
    }
    return cvtExtension
}

/**
 * 本身就是一个File，哪怕是系统路径都是可以直接读取。
 */
private fun Uri.copyToCacheFileSchemeFile(size:LongArray? = null): File? {
    val file = this.path?.let { File(it) }
    if (file != null) {
        size?.set(0, file.length())
    }
    return file
}

private fun Uri.copyFromCr(
    cr: ContentResolver,
    targetFile: File,
    param: String?,
    extension: String?, /*如果为空，则不做转换，直接拷贝。*/
    isSourceHeic: Boolean,
    size: LongArray?
) {
    try {
        cr.openInputStream(this)?.use { inputStream ->
            val fos = FileOutputStream(targetFile)
            var cvtFmt: String? = null
            if (param != null && extension != null && isSupportConvertImage(extension)) {
                when (param) {
                    URI_COPY_PARAM_ANY_TO_JPG -> {
                        if (extension != "jpg" && extension != "jpeg") {
                            cvtFmt = "jpg"
                        }
                    }

                    URI_COPY_PARAM_HEIC_TO_JPG -> {
                        if (isSourceHeic) {
                            cvtFmt = "jpg"
                        }
                    }

                    URI_COPY_PARAM_HEIC_TO_PNG -> {
                        if (isSourceHeic) {
                            cvtFmt = "png"
                        }
                    }
                }
            }

            if (cvtFmt != null) {
                copyImageAndCvtTo(inputStream, fos, cvtFmt)
            } else {
                copyFile(inputStream, fos)
            }

            fos.flush()
            fos.close()

            size?.set(0, targetFile.length())
            logt(tag = "picker") { "$targetFile $cvtFmt after copy ${size?.get(0)}" }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun isSupportConvertImage(extension: String): Boolean {
    val imageExtensions = listOf("jpg", "jpeg", "png", "heic")
    return extension in imageExtensions
}

/**
 * 将List<Uri>遍历识别，全部拷贝到本地cache；如果param有传参，则会进行转换拷贝。
 *
 * 不管是不是图片是不是进行转换，都会拷贝（file型的uri除外）。
 *
 * 本函数会耗时。自行放到scope中运行。
 */
@WorkerThread
fun List<Uri>.copyToCacheConvert(cr:ContentResolver,
                                 subCacheDir:String,
                                 copyFilePrefix:String = "copy_",
                                 param:String? = URI_COPY_PARAM_HEIC_TO_JPG) : List<Uri> {
    return this.map { uri-> uri.copyToCacheConvert(cr, param, subCacheDir, copyFilePrefix) }
}

const val URI_COPY_PARAM_HEIC_TO_JPG = "only_heic_convert_to_jpg"
const val URI_COPY_PARAM_HEIC_TO_PNG = "only_heic_convert_to_png"
const val URI_COPY_PARAM_ANY_TO_JPG = "any_convert_to_jpg"

@Throws(IOException::class)
fun copyFile(
    inputStream: InputStream,
    out: OutputStream
): Long {
    var progress: Long = 0
    val buffer = ByteArray(8192)

    var t: Int
    while ((inputStream.read(buffer).also { t = it }) != -1) {
        out.write(buffer, 0, t)
        progress += t.toLong()
    }
    return progress
}

//将图片转码
@Throws(Exception::class)
fun copyImageAndCvtTo(inputStream: InputStream, outputStream: FileOutputStream, fmt:String) {
    val options = BitmapFactory.Options()
    options.inJustDecodeBounds = false
    val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
    bitmap?.compress(if(fmt == "png") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 100, outputStream)
}


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

//
//fun getVideoThumbnail(cr:ContentResolver, file: File, width:Int, height:Int): Bitmap? {
//    return getVideoThumbnail(cr, Uri.fromFile(file), width, height)
//}
//
//fun getThumbnail(cr:ContentResolver, contentUri: Uri, width:Int, height:Int): Bitmap? {
//    try {
//        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//            cr.loadThumbnail(contentUri, Size(width, height), null)
//        } else {
//            val path = contentUri.path
//            if (path == null) {
//                null
//            } else {
//                ThumbnailUtils.createVideoThumbnail(path, MediaStore.Images.Thumbnails.MINI_KIND)
//            }
//        }
//    } catch (e:Exception) {
//        e.printStackTrace()
//    }
//    return null
//}
//
//@Throws(IOException::class)
//fun createThumbnailFromImageInputStream(inputStream: InputStream?, width: Int, height: Int): Bitmap? {
//    val options = BitmapFactory.Options()
//    options.inJustDecodeBounds = true
//    // Calculate inSampleSize
//    options.inSampleSize = calculateInSampleSize(options, width, height)
//
//    // Decode bitmap with inSampleSize set
//    options.inJustDecodeBounds = false
//    return BitmapFactory.decodeStream(inputStream, null, options)
//}
//
//private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
//    // Raw height and width of image
//    val height = options.outHeight
//    val width = options.outWidth
//    var inSampleSize = 1
//
//    if (height > reqHeight || width > reqWidth) {
//        val halfHeight = height / 2
//        val halfWidth = width / 2
//
//        // Calculate the largest inSampleSize value that is a power of 2 and keeps both
//        // height and width larger than the requested height and width.
//        while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
//            inSampleSize *= 2
//        }
//    }
//
//    return inSampleSize
//}

