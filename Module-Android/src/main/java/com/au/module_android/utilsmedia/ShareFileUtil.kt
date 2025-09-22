package com.au.module_android.utilsmedia

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.au.module_android.utils.startActivityFix
import com.au.module_android.utilsmedia.MediaHelper.Companion.getMimeTypePath
import java.io.File

/**
 * 将本地文件分享出去。
 * 一定是要在有分享权限的路径下。如果没有的话，参考xml/file_path.xml的写法。
 *
 * @param file 本应用范围内的文件
 */
fun shareFile(context: Context, file: File?, title:String) {
    if (file != null && file.exists()) {
        val share = Intent(Intent.ACTION_SEND)
        val uri: Uri?

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // "项目包名.fileprovider"即是在清单文件中配置的authorities
            uri = FileProvider.getUriForFile(context, context.packageName + ".provider", file)
            // 给目标应用一个临时授权
        } else {
            uri = Uri.fromFile(file)
        }

        share.putExtra(Intent.EXTRA_STREAM, uri)
        share.type = getMimeTypePath(file.absolutePath) // 此处可发送多种文件
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivityFix(Intent.createChooser(share, title))
    }
}

/**
 * 把本地的文件，使用其他应用打开
 */
fun openWith(context: Context, file: File, buildConfigApplicationId:String, title:String) {
    val intent = Intent(Intent.ACTION_VIEW)
    val uri:Uri
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        //第二个参数是manifest中定义的`authorities`
        uri = FileProvider.getUriForFile(
            context,
            "${buildConfigApplicationId}.provider",
            file)
    } else {
        uri = file.toUri()
    }
//    intent.putExtra(Intent.EXTRA_STREAM, uri) //No Need
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    intent.setData(uri)
    val chooserIntent = Intent.createChooser(intent, title)
    context.startActivityFix(chooserIntent)
}