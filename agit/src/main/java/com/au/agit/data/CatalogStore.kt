package com.au.agit.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.au.agit.model.Catalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.agitDataStore by preferencesDataStore(name = "agit_catalog")

/** 持久化项目目录索引、加密凭据与编辑会话；不把失败读取当作空数据覆盖。 */
class CatalogStore(context: Context) {
    private val store = context.agitDataStore
    private val key = stringPreferencesKey("catalog")
    private val json = Json { ignoreUnknownKeys = true }
    val catalogFlow: Flow<Catalog> = store.data.map { preferences ->
        preferences[key]?.let { json.decodeFromString<Catalog>(it) } ?: Catalog()
    }

    suspend fun update(transform: (Catalog) -> Catalog) {
        store.edit { preferences ->
            val current = preferences[key]?.let { json.decodeFromString<Catalog>(it) } ?: Catalog()
            preferences[key] = json.encodeToString(Catalog.serializer(), transform(current))
        }
    }
}
