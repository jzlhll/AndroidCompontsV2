package com.allan.picnote.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.nodeGuideDataStore by preferencesDataStore(name = "node_guide")

enum class NodeGuideKind(val preferencePrefix: String) { Menu("menu"), Card("card") }

/** 分别持久化菜单和卡片的点击总数、引导阶段及首次确认时的点击总数。 */
class NodeGuidePreferences(context: Context) {
    private val store = context.applicationContext.nodeGuideDataStore

    suspend fun hasUnfinishedGuide(): Boolean {
        val values = store.data.first()
        return NodeGuideKind.entries.any { kind ->
            (values[longPreferencesKey("${kind.preferencePrefix}_guide_stage")] ?: 0L) < 2L
        }
    }

    suspend fun recordClick(kind: NodeGuideKind) {
        val key = longPreferencesKey("${kind.preferencePrefix}_click_count")
        store.edit { values ->
            val count = values[key] ?: 0L
            val updated = if (count < Long.MAX_VALUE) count + 1L else count
            values[key] = updated
            val stageKey = longPreferencesKey("${kind.preferencePrefix}_guide_stage")
            val stage = values[stageKey] ?: 0L
            if (stage < 2L) {
                val clicksAtAcknowledgement = values[longPreferencesKey("${kind.preferencePrefix}_clicks_at_acknowledgement")] ?: 0L
                if (updated > 2L || (stage == 1L && updated > clicksAtAcknowledgement)) values[stageKey] = 2L
            }
        }
    }

    suspend fun guideForStartup(eligibleDocument: Boolean): NodeGuideKind? {
        if (!eligibleDocument) return null
        val values = store.data.first()
        return NodeGuideKind.entries.firstOrNull { kind ->
            val stage = values[longPreferencesKey("${kind.preferencePrefix}_guide_stage")] ?: 0L
            if (stage >= 2L) return@firstOrNull false
            val clicks = values[longPreferencesKey("${kind.preferencePrefix}_click_count")] ?: 0L
            val clicksAtAcknowledgement = values[longPreferencesKey("${kind.preferencePrefix}_clicks_at_acknowledgement")] ?: 0L
            clicks <= 2L && (stage == 0L || clicks == clicksAtAcknowledgement)
        }
    }

    suspend fun acknowledgeGuide(kind: NodeGuideKind) {
        val stage = longPreferencesKey("${kind.preferencePrefix}_guide_stage")
        val clicks = longPreferencesKey("${kind.preferencePrefix}_click_count")
        val clicksAtAcknowledgement = longPreferencesKey("${kind.preferencePrefix}_clicks_at_acknowledgement")
        store.edit { values ->
            val count = values[stage] ?: 0L
            if (count < 2L) values[stage] = count + 1L
            if (count == 0L) values[clicksAtAcknowledgement] = values[clicks] ?: 0L
        }
    }
}
