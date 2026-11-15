package com.allan.picnote.ui

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

const val EditorOverlayMetadataKey = "picnote.editorOverlay"

/** 保留首页 entry，在同一 Compose 窗口的上层呈现节点编辑 entry。 */
class EditorOverlaySceneStrategy : SceneStrategy<NavKey> {
    override fun SceneStrategyScope<NavKey>.calculateScene(entries: List<NavEntry<NavKey>>): Scene<NavKey>? {
        val entry = entries.lastOrNull() ?: return null
        if (entry.metadata[EditorOverlayMetadataKey] != true || entries.size < 2) return null
        val previous = entries.dropLast(1)
        return EditorOverlayScene(entry.contentKey, entry, previous, previous)
    }
}

private data class EditorOverlayScene(
    override val key: Any,
    val entry: NavEntry<NavKey>,
    override val previousEntries: List<NavEntry<NavKey>>,
    override val overlaidEntries: List<NavEntry<NavKey>>,
) : OverlayScene<NavKey> {
    override val entries: List<NavEntry<NavKey>> = listOf(entry)
    override val content: @Composable () -> Unit = { entry.Content() }
}
