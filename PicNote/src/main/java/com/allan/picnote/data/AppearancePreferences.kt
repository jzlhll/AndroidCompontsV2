package com.allan.picnote.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.au.module_cached.AppDataStore
import com.au.module_androiduiex.styles.ComposeTheme

private val Context.appearanceDataStore by preferencesDataStore(name = "appearance")

/** 使用独立 DataStore 保存外观，不读取或初始化 Globals。 */
class AppearancePreferences(context: Context) {
    private val store = context.applicationContext.appearanceDataStore

    suspend fun readTheme(): ComposeTheme {
        val name = AppDataStore.readString("theme", null, store)
        return ComposeTheme.entries.firstOrNull { it.name == name } ?: ComposeTheme.Sage
    }

    suspend fun saveTheme(theme: ComposeTheme) {
        AppDataStore.saveStringSuspend("theme", theme.name, store)
    }
}
