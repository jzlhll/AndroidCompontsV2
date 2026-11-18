package com.allan.picnote

import com.au.module_android.Globals
import com.au.module_android.log.logd as commonLogd
import com.au.module_android.log.loge as commonLoge
import com.au.module_android.log.logEx as commonLogEx

fun logd(message: String) {
    Globals.app.commonLogd { message }
}

fun loge(message: String) {
    Globals.app.commonLoge { message }
}

fun logEx(throwable: Throwable, message: String) {
    Globals.app.commonLogEx(throwable = throwable) { message }
}
