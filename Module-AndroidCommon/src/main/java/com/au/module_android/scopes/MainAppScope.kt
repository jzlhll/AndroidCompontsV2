package com.au.module_android.scopes

import com.au.module_android.log.logEx
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

// 主线程 Scope
class MainAppScope(
    private val exceptionPrefix: String = "main app scope catch: ") : CoroutineScope {
    private val job = SupervisorJob()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main.immediate + job + CoroutineExceptionHandler { _, throwable ->
            logEx(throwable = throwable) { exceptionPrefix }
        }

    fun cancel() {
        job.cancel()
    }
}