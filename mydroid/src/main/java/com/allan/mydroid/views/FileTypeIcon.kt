package com.allan.mydroid.views

import androidx.annotation.DrawableRes
import com.allan.mydroid.R
import com.au.module_android.utilsmedia.ExtensionMimeUtil

@DrawableRes
fun getIcon(fileName: String?): Int {
    // 提取文件后缀并转为小写（处理无后缀的情况）
    val extension = fileName?.substringAfterLast('.', "")?.lowercase() ?: ""
    return when (extension) {
        // 文本/文档类型
        "ppt", "pptx" -> R.drawable.ic_filetype_ppt
        "doc", "docx" -> R.drawable.ic_filetype_doc
        "xls", "xlsx" -> R.drawable.ic_filetype_xls
        "pdf" -> R.drawable.ic_filetype_pdf
        "txt", "log", "md" -> R.drawable.ic_filetype_txt
        //压缩包
        "zip" -> R.drawable.ic_filetype_zip
        // 压缩包其他
        "rar", "tar", "gz", "7z" -> R.drawable.ic_filetype_archive
        // 代码文件类型（可选扩展）
        "java", "kt", "py", "js", "html", "css" -> R.drawable.ic_filetype_code
        "exe" -> R.drawable.ic_filetype_exe
        "csv" -> R.drawable.ic_filetype_csv
        "rtf" -> R.drawable.ic_filetype_rtf
        "mp4" -> R.drawable.ic_filetype_mp4
        // 其他类型
        else ->
            if (ExtensionMimeUtil.isImageFileSimple(extension)) {
                R.drawable.ic_filetype_img
            } else if (ExtensionMimeUtil.isAudioFileSimple(extension)) {
                R.drawable.ic_filetype_audio
            } else if (ExtensionMimeUtil.isVideoFileSimple(extension)) {
                R.drawable.ic_filetype_video
            } else {
                R.drawable.ic_filetype_other
            }
    }
}
