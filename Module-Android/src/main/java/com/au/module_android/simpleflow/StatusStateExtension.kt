package com.au.module_android.simpleflow
import kotlinx.coroutines.flow.SharedFlow

suspend fun <T> SharedFlow<StatusState<T>>.collectStatusState(loading: ()->Unit = {},
                                                              success: (data:T) -> Unit,
                                                              error: (exMsg:String?) -> Unit): Nothing {
    collect {
        when (it) {
            is StatusState.Loading -> {
                loading()
            }
            is StatusState.Success -> {
                success(it.data)
            }
            is StatusState.Error -> {
                error(it.message)
            }
        }
    }
}

suspend fun <T> stateRunCatching(errCallback:((String?)->Unit)? = null, requestBlock: suspend () -> T) : StatusState<T>{
    try {
        val data = requestBlock()
        return StatusState.Success(data)
    } catch (e: Exception) {
        errCallback?.invoke(e.message)
        return StatusState.Error(e.message)
    }
}