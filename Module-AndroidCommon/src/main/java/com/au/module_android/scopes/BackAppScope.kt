package com.au.module_android.scopes

import com.au.module_android.log.logEx
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

// 后台线程 Scope
class BackAppScope(
    private val exceptionPrefix: String = "Coroutine catch: ") : CoroutineScope {
    private val job = SupervisorJob()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + job + CoroutineExceptionHandler { _, throwable ->
            logEx(throwable = throwable) { exceptionPrefix }
        }

    fun cancel() {
        job.cancel()
    }
}