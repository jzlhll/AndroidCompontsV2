package com.allan.picnote.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.nodeGuideDataStore by preferencesDataStore(name = "node_guide")
private const val GuideClickLimit = 2L
private const val GuideCompletedStage = 2L

enum class NodeGuideKind(prefix: String) {
    Menu("menu"),
    Card("card");

    val clickCountKey = longPreferencesKey("${prefix}_click_count")
    val stageKey = longPreferencesKey("${prefix}_guide_stage")
    val clicksAtAcknowledgementKey = longPreferencesKey("${prefix}_clicks_at_acknowledgement")
}

/** 分别持久化菜单和卡片的点击总数、引导阶段及首次确认时的点击总数。 */
class NodeGuidePreferences(context: Context) {
    private val store = context.applicationContext.nodeGuideDataStore

    suspend fun recordClick(kind: NodeGuideKind) {
        val clickCount = kind.clickCountKey
        store.edit { values ->
            val count = values[clickCount] ?: 0L
            val updated = if (count < Long.MAX_VALUE) count + 1L else count
            values[clickCount] = updated
            val stageKey = kind.stageKey
            val stage = values[stageKey] ?: 0L
            if (stage < GuideCompletedStage) {
                val clicksAtAcknowledgement = values[kind.clicksAtAcknowledgementKey] ?: 0L
                if (updated > GuideClickLimit || (stage == 1L && updated > clicksAtAcknowledgement)) {
                    values[stageKey] = GuideCompletedStage
                }
            }
        }
    }

    suspend fun guideForStartup(): NodeGuideKind? {
        val values = store.data.first()
        return NodeGuideKind.entries.firstOrNull { kind ->
            val stage = values[kind.stageKey] ?: 0L
            if (stage >= GuideCompletedStage) return@firstOrNull false
            val clicks = values[kind.clickCountKey] ?: 0L
            val clicksAtAcknowledgement = values[kind.clicksAtAcknowledgementKey] ?: 0L
            clicks <= GuideClickLimit && (stage == 0L || clicks == clicksAtAcknowledgement)
        }
    }

    suspend fun acknowledgeGuide(kind: NodeGuideKind) {
        val stageKey = kind.stageKey
        store.edit { values ->
            val stage = values[stageKey] ?: 0L
            if (stage < GuideCompletedStage) values[stageKey] = stage + 1L
            if (stage == 0L) values[kind.clicksAtAcknowledgementKey] = values[kind.clickCountKey] ?: 0L
        }
    }
}
