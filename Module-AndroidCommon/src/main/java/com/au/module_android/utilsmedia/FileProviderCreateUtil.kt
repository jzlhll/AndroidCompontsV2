package com.au.module_android.utilsmedia

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.au.module_android.Globals
import java.io.File

/**
 * 创建一个图片的临时的uri。存在的话，就会删除再新建给出。
 * dir就是目录；fileName就是文件名；extension后缀名（记得自行带点，比如.png）
 */
fun getPictureFileUri(context: Context, dir:String, fileName:String, extension:String) : Uri? {
    return getPictureFileUri(context, File(dir), fileName, extension)
}

/**
 * 创建一个图片的临时的uri。存在的话，就会删除再新建给出。
 * dir就是目录；fileName就是文件名；extension后缀名（记得自行带点，比如.png）
 */
fun getPictureFileUri(context: Context, dir:File, fileName:String, extension:String) : Uri? {
    val photoFile = File.createTempFile(fileName, extension, dir).apply {
        createNewFile()
        deleteOnExit()
    }
    return getUriFromFile(context, photoFile)
}

fun getUriFromFile(context: Context, file: File?): Uri? {
    return getUriFromFile(context, null, file)
}

/**
 * 获取File的Uri
 *
 * @param context 上下文
 * @param file    文件
 * @return Uri
 */
fun getUriFromFile(context: Context, authority: String?, file: File?): Uri? {
    if (file == null) {
        return null
    }
    var authorityTmp = authority
    if (authorityTmp.isNullOrEmpty()) {
        authorityTmp = context.packageName + ".fileprovider"
    }
    try {
        return FileProvider.getUriForFile(context, authorityTmp, file)
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

/**
 * Resource to uri.
 * res2Uri([res type]/[res name]) -> res2Uri(drawable/icon), res2Uri(raw/icon)
 * res2Uri([resource_id]) -> res2Uri(R.drawable.icon)
 * @param resPath The path of res.
 * @return uri
 */
fun res2Uri(resPath: String): Uri {
    return ("android.resource://" + Globals.app.packageName + "/" + resPath).toUri()
}