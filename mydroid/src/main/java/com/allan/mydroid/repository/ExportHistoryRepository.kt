package com.allan.mydroid.repository

import com.au.module_cached.AppDataStore
import com.au.module_cached.AppDataStore.globalDataStore
import com.au.module_android.Globals
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 通过 DataStore 原子更新导出历史，避免并发导出覆盖记录。 */
class ExportHistoryRepository {
    private companion object {
        const val KEY_EXPORT_HISTORY = "my_droid_export_history_list"
    }

    suspend fun loadExportHistory(): String {
        return AppDataStore.readString(KEY_EXPORT_HISTORY, "") ?: ""
    }

    suspend fun writeNewExportHistory(info: String) {
        val currentTimeMillis = System.currentTimeMillis()
        val formatter = DateTimeFormatter
            .ofPattern("yyyyMMdd HH:mm")
            .withZone(ZoneId.systemDefault())
        val formattedTime = formatter.format(Instant.ofEpochMilli(currentTimeMillis))

        val key = stringPreferencesKey(KEY_EXPORT_HISTORY)
        Globals.app.globalDataStore.edit { preferences ->
            val old = preferences[key].orEmpty()
            val lines = old.split("\n")
            val retained = if (lines.size > 100) lines.take(80).joinToString("\n") else old
            preferences[key] = "($formattedTime) $info\n\n$retained"
        }
    }
}
