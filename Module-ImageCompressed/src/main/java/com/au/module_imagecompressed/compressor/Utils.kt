package com.au.module_imagecompressed.compressor

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.annotation.WorkerThread
import com.au.module_android.utils.NoWayException
import com.au.module_android.utils.copyFile
import com.au.module_android.utilsmedia.myParse
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream

/**
 * 经过研究，android对于content uri想要使用File最好的办法，就是拷贝到自己的目录下。
 * 才是最保险的，而且不需要考虑权限问题。
 *
 * 将Uri拷贝到CompressCache目录中去。如果该Uri就是我app内的File，不做拷贝。
 *
 * 自行考虑放到Scope中运行。可能会耗时比较多，比如拷贝视频。
 *
 * @return 不太可能是空。
 */
@WorkerThread
internal suspend fun Uri.copyToCacheFile(context: Context): File {
    delay(0)
    val parsedInfo = this.myParse(context)
    if (this.scheme == ContentResolver.SCHEME_FILE) {
        val path = parsedInfo.fullPath ?: (parsedInfo.relativePath ?: this.path)
        if (path != null) {
            return File(path)
        } else {
            throw RuntimeException("Error when copy to cache path is null!")
        }
    } else if (this.scheme == ContentResolver.SCHEME_CONTENT) {
        val file = copyFromContentUri(context.contentResolver, this, parsedInfo.name, parsedInfo.extension)
        if (file != null) {
            return file
        } else {
            throw RuntimeException("Error when copy to cache path $this failed!")
        }
    }
    throw NoWayException()
}

private fun copyFromContentUri(cr: ContentResolver, uri: Uri, origName:String, extension:String) : File?{
    cr.openInputStream(uri)?.use { inputStream ->
        val targetFile = CompressCacheConstManager.createCopyOutputFile(origName, extension)
        FileOutputStream(targetFile).use { fos->
            copyFile(inputStream, fos)
            fos.flush()
        }
        return targetFile
    }
    return null
}
