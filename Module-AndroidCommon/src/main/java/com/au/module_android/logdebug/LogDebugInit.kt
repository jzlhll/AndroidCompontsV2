package com.au.module_android.logdebug

import com.au.module_android.Globals
import java.io.File

class LogDebugInit {
    fun initAsDebug(debugLog: Boolean = true, debugFile: Boolean = true) {
        if (debugLog) {
            val logDebug = Globals.goodFilesDir.absolutePath + File.separator + "hasAuDebugLogMask"
            val logDebugFile = File(logDebug)
            if (!logDebugFile.exists()) {
                logDebugFile.mkdirs()
            }
        }

        if (debugFile) {
            val logFileDebug = Globals.goodFilesDir.absolutePath + File.separator + "hasAuLogFileMask"
            val logFileDebugFile = File(logFileDebug)
            if (!logFileDebugFile.exists()) {
                logFileDebugFile.mkdirs()
            }
        }
    }
}