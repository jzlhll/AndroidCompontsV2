package com.allan.picnote.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.au.module_cached.AppDataStore

enum class ImportMode { AddOnly, DeleteSelected, DeleteBatch }

private val Context.importModeDataStore by preferencesDataStore(name = "import_mode")

/** 保存待选图片的添加模式；旧版本与未知值均使用只添加。 */
class ImportModePreferences(context: Context) {
    private val store = context.applicationContext.importModeDataStore

    suspend fun readMode(): ImportMode {
        val name = AppDataStore.readString("mode", null, store)
        return ImportMode.entries.firstOrNull { it.name == name } ?: ImportMode.AddOnly
    }

    suspend fun saveMode(mode: ImportMode) {
        AppDataStore.saveStringSuspend("mode", mode.name, store)
    }
}
