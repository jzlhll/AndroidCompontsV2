package com.au.module_imagecompressed

import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import com.au.module_imagecompressed.compressor.CompressCacheConstManager

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.annotation.WorkerThread
import com.au.module_android.utils.NoWayException
import com.au.module_android.utils.copyFile
import com.au.module_android.utilsmedia.UriParseHelper
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
    val parsedInfo = UriParseHelper().parse(context.contentResolver, this)
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

// 检查照片选择器是否可用
fun isPhotoPickerAvailable(context: Context) : Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // 在 Android 13 (API 33) 及以上版本中，照片选择器始终可用
        true
    } else {
        // 对于 Android 11 (API 30) 到 Android 12L (API 32)
        // 需要通过 Google 系统更新接收模块化系统组件的设备才可能可用
        // 对于更旧的版本 (API 19-29) 和某些 Android Go 设备，可能通过 Google Play 服务获得后向移植版本
        // 实际开发中应使用 AndroidX 库中的方法进行检查
        val availability = ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)
        availability
    }
}