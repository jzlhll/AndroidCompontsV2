package com.au.agit.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.au.agit.R
import com.au.agit.model.ChangeType
import com.au.agit.model.CommitDiffKind
import com.au.agit.ui.components.AGitIconButton
import com.au.agit.ui.components.changeColor

private const val DIFF_LINE_LIMIT = 500
private const val DIFF_LINE_LENGTH_LIMIT = 1000

@Composable
fun FileDiffRoute(key: DiffKey, model: RepositoryViewModel, onBack: () -> Unit) {
    val state by model.diffFlow.collectAsStateWithLifecycle()
    LaunchedEffect(key) { model.refreshDiff(key.path, key.oldPath) }
    PageScaffold(stringResource(R.string.file_diff_title), onBack, actions = {
        AGitIconButton(onClick = { model.refreshDiff(key.path, key.oldPath) }, enabled = !state.loading) {
            Icon(Icons.Default.Refresh, stringResource(R.string.refresh))
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(key.path, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium)
                key.oldPath?.let { Text(stringResource(R.string.change_renamed, it), style = MaterialTheme.typography.bodyMedium) }
                Text(stringResource(R.string.file_diff_comparison), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error) }
            state.diff?.let { diff ->
                diff.modeChange?.let { (before, after) ->
                    Text(stringResource(R.string.file_diff_mode, before, after), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
                val message = when (diff.kind) {
                    CommitDiffKind.Text -> null
                    CommitDiffKind.Binary -> R.string.file_diff_binary
                    CommitDiffKind.TooLarge -> R.string.file_diff_too_large
                    CommitDiffKind.Unchanged -> R.string.file_diff_unchanged
                    CommitDiffKind.MetadataOnly -> R.string.file_diff_metadata_only
                }
                if (message != null) {
                    Text(stringResource(message), modifier = Modifier.padding(16.dp))
                } else {
                    val lines = remember(diff.patch) { diff.patch.lineSequence().take(DIFF_LINE_LIMIT + 1).toList() }
                    if (diff.truncated || lines.size > DIFF_LINE_LIMIT || lines.any { it.length > DIFF_LINE_LENGTH_LIMIT }) {
                        Text(stringResource(R.string.file_diff_truncated), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
                    }
                    SelectionContainer(Modifier.weight(1f)) {
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                            items(lines.take(DIFF_LINE_LIMIT)) { line ->
                                val color = when {
                                    line.startsWith('+') -> changeColor(ChangeType.Added)
                                    line.startsWith('-') -> changeColor(ChangeType.Deleted)
                                    line.startsWith("@@") -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                                Text(line.take(DIFF_LINE_LENGTH_LIMIT), color = color, fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
