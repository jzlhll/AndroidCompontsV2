package com.allan.mydroid.beansinner

import androidx.annotation.Keep
import com.au.module_nested.recyclerview.IViewTypeBean
import java.io.File

@Keep
data class MergedFileInfo(val file: File,
                          val md5:String,
                          val fileSizeInfo:String) : IViewTypeBean