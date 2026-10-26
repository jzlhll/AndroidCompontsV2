package com.allan.mydroid.beansinner

import androidx.annotation.Keep
import com.au.module_nested.recyclerview.IViewTypeBean
import java.io.File

@Keep
data class MergedFileInfo(val file: File,
                          val md5:String,
                          val fileSizeInfo:String) : IViewTypeBean {

    companion object {
        /** 列表加载只读取元数据，摘要由传输完成时保存的快照提供。 */
        fun fromCacheFile(file: File, fileSizeInfo: String, md5: String = ""): MergedFileInfo {
            return MergedFileInfo(file, md5, fileSizeInfo)
        }
    }
}
