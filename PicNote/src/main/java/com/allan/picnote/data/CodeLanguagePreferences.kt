package com.allan.picnote.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.codeLanguageDataStore by preferencesDataStore(name = "code_language_usage")

/** 保存语言使用权重，未使用过的语言沿用固定的默认排名。 */
class CodeLanguagePreferences(context: Context) {
    private val store = context.applicationContext.codeLanguageDataStore
    private val defaultWeights = DefaultCodeLanguageOrder.withIndex().associate { (index, language) ->
        language to (DefaultCodeLanguageOrder.size - index).toLong()
    }

    val languageOrderFlow = store.data.map { preferences ->
        DefaultCodeLanguageOrder.sortedByDescending { language ->
            preferences[weightKey(language)] ?: defaultWeights.getValue(language)
        }
    }

    suspend fun recordSelection(language: String) {
        val defaultWeight = defaultWeights[language] ?: return
        // 点击即保存，离开编辑页时仍完成这一次短事务。
        withContext(NonCancellable) {
            store.edit { preferences ->
                val key = weightKey(language)
                val weight = preferences[key] ?: defaultWeight
                val increment = if (weight > 1000L) 100L else 1000L
                preferences[key] = if (weight > Long.MAX_VALUE - increment) Long.MAX_VALUE else weight + increment
            }
        }
    }

    private fun weightKey(language: String) = longPreferencesKey("weight_$language")
}
