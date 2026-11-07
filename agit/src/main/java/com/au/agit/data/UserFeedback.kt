package com.au.agit.data

import com.au.module_android.log.loge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class Notice(val id: String = UUID.randomUUID().toString(), val message: String)

/** 保存尚未展示的反馈；由当前可见窗口调用模块 Toast，避免弹窗遮挡提示。 */
class UserFeedback {
    private val mutableNotice = MutableStateFlow<Notice?>(null)
    val noticeFlow = mutableNotice.asStateFlow()
    fun notify(message: String) { mutableNotice.value = Notice(message = message) }
    fun acknowledge(id: String) { if (mutableNotice.value?.id == id) mutableNotice.value = null }
    fun failure(kind: String, error: Throwable, projectId: String? = null, traceId: String = "none") {
        val failure = safeFailure(error)
        loge { "action failed trace=$traceId kind=$kind project=${projectId ?: "none"} code=${failure.code} type=${error.javaClass.simpleName}" }
        notify(failure.message)
    }
}
