package com.au.agit.ui

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.au.agit.R
import com.au.agit.appString
import com.au.agit.data.LogFileInfo
import com.au.agit.data.LogLine
import com.au.agit.model.AGitException
import com.au.agit.model.ChangeType
import com.au.agit.ui.components.changeColor
import com.au.agit.ui.components.formatFileSize
import com.au.agit.ui.theme.AGitTheme
import com.au.module_android.utilsmedia.getUriFromFile
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.CancellationException
import com.au.agit.ui.components.AGitIconButton as IconButton
import com.au.agit.ui.components.AGitTextButton as TextButton

@Composable
fun LogsRoute(model: LogsViewModel, app: AppViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val state by model.stateFlow.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_START) { model.reload() }
    LaunchedEffect(state.sharedFile) {
        val file = state.sharedFile ?: return@LaunchedEffect
        try {
            val uri = getUriFromFile(context, file) ?: throw AGitException(appString(R.string.log_export_failed), "LOG_EXPORT")
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(file.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, appString(R.string.log_share)))
        } catch (error: CancellationException) { throw error } catch (error: Exception) { app.failure("log share", error) }
        finally { model.acknowledgeShare() }
    }
    LogsScreen(state, onBack, onOpen, onRefresh = { model.reload() }, onDelete = model::delete, onShare = model::share)
}

@Composable
fun LogsScreen(
    state: LogsUiState,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onRefresh: () -> Unit,
    onDelete: (String) -> Unit,
    onShare: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var actionFile by remember { mutableStateOf<LogFileInfo?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val groups = remember(state.files) { state.files.groupBy { Instant.ofEpochMilli(it.modifiedAt).atZone(ZoneId.systemDefault()).toLocalDate() } }
    PageScaffold(stringResource(R.string.logs_title), onBack, modifier, actions = {
        IconButton(onClick = onRefresh, enabled = !state.loading && !state.busy) { Icon(Icons.Default.Refresh, stringResource(R.string.refresh)) }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (selecting) Text(stringResource(R.string.log_select_limit), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                if (selecting) TextButton(onClick = { onShare(selected.toSet()) }, enabled = selected.isNotEmpty() && !state.busy) { Text(stringResource(R.string.log_share)) }
                TextButton(onClick = { selecting = !selecting; selected = emptyList() }, enabled = !state.busy) { Text(stringResource(if (selecting) R.string.cancel else R.string.log_select)) }
            }
            if (state.loading || state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(20.dp)) }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 20.dp)) {
                if (state.files.isEmpty() && !state.loading) item { Text(stringResource(R.string.log_empty), modifier = Modifier.padding(24.dp)) }
                groups.forEach { (date, files) ->
                    item(key = "day-$date") { Text(date.format(dateFormat), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) }
                    items(files, key = { it.name }) { file ->
                        OutlinedCard(onClick = {
                            if (selecting) {
                                selected = when { file.name in selected -> selected - file.name; selected.size < 3 -> selected + file.name; else -> selected }
                            } else actionFile = file
                        }, enabled = !state.busy, colors = CardDefaults.outlinedCardColors(containerColor = if (file.name in selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (selecting) Checkbox(file.name in selected, onCheckedChange = null, modifier = Modifier.padding(end = 12.dp))
                                Text(file.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text(formatFileSize(file.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
    actionFile?.let { file ->
        AlertDialog(onDismissRequest = { actionFile = null }, title = { Text(file.name) },
            confirmButton = { TextButton(onClick = { actionFile = null; onOpen(file.name) }) { Text(stringResource(R.string.log_view)) } },
            dismissButton = { TextButton(onClick = { actionFile = null; deleting = file.name }) { Text(stringResource(R.string.log_delete)) } })
    }
    deleting?.let { name ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.log_delete)) }, text = { Text(stringResource(R.string.log_delete_confirm, name)) },
            confirmButton = { TextButton(onClick = { deleting = null; onDelete(name) }) { Text(stringResource(R.string.log_delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } })
    }
}

@Composable
fun LogFileRoute(name: String, model: LogsViewModel, onBack: () -> Unit) {
    val state by model.stateFlow.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_START) { model.reload() }
    LogFileScreen(name, state, onBack, onRefresh = { model.reload() }, onOlder = { model.reload(older = true) })
}

@Composable
fun LogFileScreen(name: String, state: LogsUiState, onBack: () -> Unit, onRefresh: () -> Unit, onOlder: () -> Unit, modifier: Modifier = Modifier) {
    var menu by remember { mutableStateOf(false) }
    var wrap by rememberSaveable { mutableStateOf(false) }
    var time by rememberSaveable { mutableStateOf(true) }
    var thread by rememberSaveable { mutableStateOf(true) }
    var level by rememberSaveable { mutableStateOf(true) }
    var tag by rememberSaveable { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val horizontal = rememberScrollState()
    LaunchedEffect(state.lines.lastOrNull()?.id) { if (state.lines.isNotEmpty()) listState.scrollToItem(state.lines.lastIndex) }
    PageScaffold(name, onBack, modifier, actions = {
        IconButton(onClick = onRefresh, enabled = !state.loading) { Icon(Icons.Default.Refresh, stringResource(R.string.refresh)) }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.log_display)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                listOf(Triple(R.string.log_wrap, wrap) { wrap = !wrap }, Triple(R.string.log_time, time) { time = !time },
                    Triple(R.string.log_thread, thread) { thread = !thread }, Triple(R.string.log_level, level) { level = !level }, Triple(R.string.log_tag, tag) { tag = !tag }).forEach { (label, checked, toggle) ->
                    DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = toggle, trailingIcon = { if (checked) Icon(Icons.Default.Check, null) })
                }
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            if (state.olderOffset > 0) TextButton(onClick = onOlder, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.log_load_older)) }
            if (state.lines.isEmpty() && !state.loading) Text(stringResource(R.string.log_empty), modifier = Modifier.padding(24.dp))
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val viewportWidth = maxWidth
                Box(Modifier.fillMaxSize().horizontalScroll(horizontal, enabled = !wrap)) {
                    LazyColumn(state = listState, modifier = if (wrap) Modifier.width(viewportWidth) else Modifier.widthIn(min = viewportWidth), contentPadding = PaddingValues(12.dp)) {
                        items(state.lines, key = { it.id }) { line ->
                            val text = if (line.info == null) line.original else buildList {
                                if (time) add(line.info.time)
                                if (thread) add(line.info.threadProcess)
                                if (level) add(line.info.level)
                                if (tag) add(line.info.tag)
                                add(line.info.msg)
                            }.joinToString(" ")
                            SelectionContainer {
                                Text(text, softWrap = wrap, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                                    color = when (line.info?.level) { "E" -> changeColor(ChangeType.Deleted); "W" -> changeColor(ChangeType.Conflicted); else -> MaterialTheme.colorScheme.onSurface },
                                    modifier = Modifier.padding(vertical = 3.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LogsPreview() { AGitTheme { LogsScreen(LogsUiState(files = listOf(LogFileInfo("11_07.log", 10240, 1794009600000))), {}, {}, {}, {}, {}) } }

@Preview(showBackground = true)
@Composable
private fun LogFilePreview() { AGitTheme { LogFileScreen("11_07.log", LogsUiState(lines = listOf(LogLine(0, "operation completed kind=push", null))), {}, {}, {}) } }
