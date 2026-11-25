package com.allan.mydroid.state

import com.au.module_android.simpleflow.createNoStickyFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.io.File

class GlobalReceiverFlowsObj {
    val fileMergedFlow: MutableSharedFlow<File> = createNoStickyFlow()

    fun emitFileMerged(file: File) { fileMergedFlow.tryEmit(file) }
}
