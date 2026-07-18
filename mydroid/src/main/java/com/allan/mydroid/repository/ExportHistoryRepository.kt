package com.allan.mydroid.repository

import com.au.module_cached.AppDataStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ExportHistoryRepository {
    private companion object {
        const val KEY_EXPORT_HISTORY = "my_droid_export_history_list"
    }

    suspend fun loadExportHistory(): String {
        return AppDataStore.readStringBlocked(KEY_EXPORT_HISTORY, "") ?: ""
    }

    suspend fun writeNewExportHistory(info: String) {
        val old = loadExportHistory()
        val splits = old.split("\n")
        val fixOld = if (splits.size > 100) {
            val cutList = splits.subList(0, 80)
            cutList.joinToString("\n")
        } else {
            old
        }

        val currentTimeMillis = System.currentTimeMillis()
        val formatter = DateTimeFormatter
            .ofPattern("yyyyMMdd HH:mm")
            .withZone(ZoneId.systemDefault())
        val formattedTime = formatter.format(Instant.ofEpochMilli(currentTimeMillis))

        AppDataStore.saveString(KEY_EXPORT_HISTORY, "($formattedTime) $info\n\n$fixOld")
    }
}
