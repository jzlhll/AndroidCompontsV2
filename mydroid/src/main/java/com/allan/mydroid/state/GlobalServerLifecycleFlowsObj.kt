package com.allan.mydroid.state

import com.au.module_android.simpleflow.createNoStickyFlow
import kotlinx.coroutines.flow.MutableSharedFlow

class GlobalServerLifecycleFlowsObj {
    val aliveStoppedFlow: MutableSharedFlow<Unit> = createNoStickyFlow()
    fun emitAliveStopped() { aliveStoppedFlow.tryEmit(Unit) }
}
