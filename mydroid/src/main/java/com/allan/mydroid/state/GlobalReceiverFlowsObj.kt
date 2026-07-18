package com.allan.mydroid.state

import com.allan.mydroid.beansinner.ReceivingFileInfo
import com.au.module_android.simpleflow.createNoStickyFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.io.File

class GlobalReceiverFlowsObj {
    val receiverProgressFlow: MutableSharedFlow<Map<String, ReceivingFileInfo>> =
        createNoStickyFlow()
    val fileMergedFlow: MutableSharedFlow<File> = createNoStickyFlow()

    fun emitProgress(map: Map<String, ReceivingFileInfo>) { receiverProgressFlow.tryEmit(map) }
    fun emitFileMerged(file: File) { fileMergedFlow.tryEmit(file) }
    fun clearProgress() { receiverProgressFlow.tryEmit(emptyMap()) }
}