package com.allan.mydroid.beansinner

import androidx.annotation.Keep
import com.au.module_android.utils.Md5Util
import com.au.module_nested.recyclerview.IViewTypeBean
import java.io.File

@Keep
data class MergedFileInfo(val file: File,
                          val md5:String,
                          val fileSizeInfo:String) : IViewTypeBean {

    companion object {
        /** 读取合并缓存文件，保证 md5 可用于稳定 uuid */
        fun fromCacheFile(file: File, fileSizeInfo: String): MergedFileInfo {
            var md5 = Md5Util.getFileMD5(file.absolutePath)
            if (md5.isEmpty()) {
                md5 = Md5Util.md5(file.absolutePath)
            }
            return MergedFileInfo(file, md5, fileSizeInfo)
        }
    }
}