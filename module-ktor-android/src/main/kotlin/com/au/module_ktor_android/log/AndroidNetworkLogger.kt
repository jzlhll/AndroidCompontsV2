package com.au.module_ktor_android.log

import com.au.module_android.log.logd
import com.au.module_android.log.logdNoFile
import com.au.module_android.log.loge
import com.au.module_ktor_base.log.NetworkLogger

/** 仅记录执行器提供的受控摘要，不输出异常原文和带凭据的 URL。 */
class AndroidNetworkLogger(private val persistDiagnostics: Boolean = false) : NetworkLogger {
    override fun debug(message: String) {
        if (persistDiagnostics) logd { message } else logdNoFile { message }
    }
    override fun failure(message: String) { loge { message } }
}
