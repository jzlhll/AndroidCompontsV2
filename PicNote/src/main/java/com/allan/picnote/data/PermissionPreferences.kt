package com.allan.picnote.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.imagePermissionDataStore by preferencesDataStore(name = "image_permission")

data class PermissionHistory(val explanationSeen: Boolean, val requested: Boolean)

/** 仅保存图片访问用途提示与请求历史，不保存系统权限状态。 */
class PermissionPreferences(context: Context) {
    private val store = context.applicationContext.imagePermissionDataStore
    private val explanationSeen = booleanPreferencesKey("image_permission_explanation_seen")
    private val requested = booleanPreferencesKey("image_permission_requested")

    suspend fun read(): PermissionHistory = history(store.data.first())

    suspend fun markExplanationSeen(): PermissionHistory = history(store.edit { it[explanationSeen] = true })

    suspend fun markRequested(): PermissionHistory = history(store.edit {
        it[explanationSeen] = true
        it[requested] = true
    })

    private fun history(values: Preferences): PermissionHistory = PermissionHistory(values[explanationSeen] ?: false, values[requested] ?: false)
}
