package com.au.module_android.utilsmedia

import android.net.Uri

data class UriParsedInfo(val uri: Uri,
                         val name:String,
                         val fileLength:Long,
                         val extension:String,
                         val mimeType:String = "",
                         val fullPath:String? = null,
                         val relativePath:String? = null,)