package com.allan.mydroid.globals

import androidx.annotation.DrawableRes
import com.allan.mydroid.R
import com.au.module_android.Globals
import com.au.module_okhttp.api.ResultBean
import com.au.module_gson.toGsonString
import com.au.module_android.utilsmedia.ExtensionMimeUtil
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.Response.Status
import fi.iki.elonen.NanoHTTPD.newFixedLengthResponse
import java.io.File

const val CACHE_IMPORT_COPY_DIR = "nanoImport"
fun cacheImportCopyDir() = Globals.goodCacheDir.absolutePath + File.separatorChar + CACHE_IMPORT_COPY_DIR

private const val TEMP_CACHE_DIR = "nanoTmp"
fun nanoTempCacheDir()  = Globals.goodCacheDir.absolutePath + File.separatorChar + "shared" + File.separatorChar + TEMP_CACHE_DIR

private const val TEMP_CACHE_CHUNKS_DIR = "nanoChunksTmp"
fun nanoTempCacheChunksDir()  = Globals.goodCacheDir.absolutePath + File.separatorChar + "shared" + File.separatorChar + TEMP_CACHE_CHUNKS_DIR

private const val TEMP_CACHE_MERGED_DIR = "nanoMerged"
fun nanoTempCacheMergedDir()  = Globals.goodCacheDir.absolutePath + File.separatorChar + "shared" + File.separatorChar + TEMP_CACHE_MERGED_DIR

const val MIME_TYPE_JSON = "application/json; charset=UTF-8"

const val CODE_SUC = "0"
const val CODE_FAIL = "-1"
const val CODE_FAIL_RECEIVER_CHUNK = "-101"
const val CODE_FAIL_MERGE_CHUNK = "-102"
const val CODE_FAIL_MD5_CHECK = "-103"

fun ResultBean<*>.okJsonResponse() : Response{
    return newFixedLengthResponse(
        Status.OK,
        MIME_TYPE_JSON,
        this.toGsonString()
    )
}

fun ResultBean<*>.badRequestJsonResponse() : Response{
    return newFixedLengthResponse(
        Status.BAD_REQUEST,
        MIME_TYPE_JSON,
        this.toGsonString()
    )
}

fun ResultBean<*>.jsonResponse(status: Response.IStatus) : Response{
    return newFixedLengthResponse(
        status,
        MIME_TYPE_JSON,
        this.toGsonString()
    )
}

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