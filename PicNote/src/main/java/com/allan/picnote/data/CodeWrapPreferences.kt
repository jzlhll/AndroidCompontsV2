package com.allan.picnote.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

data class CodeWrapSettings(val nodeCard: Boolean = false, val document: Boolean = false)

private val Context.codeWrapDataStore by preferencesDataStore(name = "code_wrap")

/** 保存代码卡片和文档代码块各自的自动换行设置。 */
class CodeWrapPreferences(context: Context) {
    private val store = context.applicationContext.codeWrapDataStore
    private val nodeCardKey = booleanPreferencesKey("node_card")
    private val documentKey = booleanPreferencesKey("document")

    suspend fun read(): CodeWrapSettings = readSettings(store.data.first())

    suspend fun save(previous: CodeWrapSettings, updated: CodeWrapSettings): CodeWrapSettings {
        val preferences = store.edit {
            if (updated.nodeCard != previous.nodeCard) it[nodeCardKey] = updated.nodeCard
            if (updated.document != previous.document) it[documentKey] = updated.document
        }
        return readSettings(preferences)
    }

    private fun readSettings(preferences: Preferences) = CodeWrapSettings(
        nodeCard = preferences[nodeCardKey] ?: false,
        document = preferences[documentKey] ?: false,
    )
}
