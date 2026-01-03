package com.au.module_imagecompressed

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.WorkerThread
import com.au.module_android.Globals
import com.au.module_android.log.logt
import com.au.module_android.utils.copyFile
import com.au.module_android.utilsmedia.UriParseHelper
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * 将Uri识别，拷贝到本地cache；如果param有传参，则会进行转换拷贝。
 *
 * 不管是不是图片是不是进行转换，都会拷贝（file型的uri除外）。
 *
 * 本函数会耗时。自行放到scope中运行。
 */
@WorkerThread
internal fun Uri.copyToCacheConvert(cr: ContentResolver,
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
internal fun Uri.copyToCacheFile(cr: ContentResolver,
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

//将图片转码
@Throws(Exception::class)
fun copyImageAndCvtTo(inputStream: InputStream,
                      outputStream: FileOutputStream,
                      fmt:String) {
    val options = BitmapFactory.Options()
    options.inJustDecodeBounds = false
    val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
    bitmap?.compress(if(fmt == "png") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 100, outputStream)
}

private fun isSupportConvertImage(extension: String): Boolean {
    val imageExtensions = listOf("jpg", "jpeg", "png", "heic")
    return extension in imageExtensions
}

const val URI_COPY_PARAM_HEIC_TO_JPG = "only_heic_convert_to_jpg"
const val URI_COPY_PARAM_HEIC_TO_PNG = "only_heic_convert_to_png"
const val URI_COPY_PARAM_ANY_TO_JPG = "any_convert_to_jpg"
