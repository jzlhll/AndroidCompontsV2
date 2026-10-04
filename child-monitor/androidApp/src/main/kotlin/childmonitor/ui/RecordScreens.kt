package childmonitor.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import childmonitor.android.R
import childmonitor.app.MonitorApplication
import childmonitor.model.SaveState
import java.io.File
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ResultScreen(id: String, viewModel: RecordViewModel, onDone: () -> Unit, onPlayback: () -> Unit,
    onCleanup: () -> Unit, onDeleted: (Set<String>) -> Unit, exporting: Boolean, onExport: (childmonitor.data.repository.MonitorRepository.Result) -> Unit, modifier: Modifier = Modifier, celebrate: Boolean = false) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, id, celebrate) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.runtime.records.revisionFlow.collect { viewModel.loadResult(id, celebrate) }
        }
    }
    DisposableEffect(viewModel, id) { onDispose { viewModel.dismissCelebration() } }
    LaunchedEffect(state.loaded, state.result) { if (state.loaded && state.result == null && !state.failed) onDeleted(setOf(id)) }
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var purging by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.result_title), style = ComposeTypography.headlineSmall)
        if (state.celebration) Celebration(Modifier.fillMaxWidth().height(80.dp))
        val result = state.result
        if (state.failed || state.operationFailed) Text(stringResource(R.string.operation_failed), style = ComposeTypography.bodyMedium)
        if (state.loaded && result == null) Text(stringResource(R.string.record_missing), style = ComposeTypography.bodyMedium)
        if (result != null) {
            val media = result.media
            if (result.session.title.isNotBlank()) Text(result.session.title, style = ComposeTypography.titleLarge)
            if (result.session.note.isNotBlank()) Text(result.session.note, style = ComposeTypography.bodyMedium)
            TextButton({ editing = true }, enabled = !state.busy) { Text(stringResource(R.string.record_edit), style = ComposeTypography.buttonLabelLarge) }
            ExportActions(result, exporting, { onExport(result) })
            Text(if (state.busy) stringResource(R.string.media_saving) else saveStateText(media?.saveState), style = ComposeTypography.titleMedium)
            result.session.endReason?.takeIf { it != "UserStop" }?.let {
                Text(stringResource(endReasonText(it)), style = ComposeTypography.bodyMedium)
            }
            Text(stringResource(R.string.monitor_duration, formatDuration(result.session.durationUs)), style = ComposeTypography.titleLarge)
            result.evaluation?.let { evaluation ->
                Text(stringResource(when (evaluation.grade) {
                    "Great" -> R.string.evaluation_great
                    "Good" -> R.string.evaluation_good
                    "NotBad" -> R.string.evaluation_not_bad
                    else -> R.string.evaluation_complete
                }), style = ComposeTypography.headlineSmall)
                Text(stringResource(when (evaluation.grade) {
                    "Great" -> R.string.encourage_great
                    "Good" -> R.string.encourage_good
                    "NotBad" -> R.string.encourage_not_bad
                    else -> R.string.encourage_complete
                }, formatDuration(evaluation.durationUs)), style = ComposeTypography.bodyLarge)
                if (evaluation.grade != null && evaluation.grade != "Great") {
                    val item = evaluation.items.filter { it.needsSuggestion }.sortedByDescending { it.abnormalUs.toDouble() / it.validUs }.firstOrNull()
                    val suggestion = when {
                        item?.kind?.name == "HeadDown" -> R.string.suggest_head_down
                        item?.kind?.name == "HeadTilt" -> R.string.suggest_head_tilt
                        item?.kind?.name == "BodyLean" -> R.string.suggest_body_lean
                        evaluation.timeoutAwayCount > 0 -> R.string.suggest_away
                        else -> null
                    }
                    if (suggestion != null) Text(stringResource(suggestion), style = ComposeTypography.bodyMedium)
                }
                if (evaluation.partialData) Text(stringResource(R.string.partial_data), style = ComposeTypography.bodyMedium)
                Text(stringResource(R.string.result_durations, formatDuration(evaluation.seatedUs), formatDuration(evaluation.awayUs),
                    formatDuration(evaluation.restUs), formatDuration(evaluation.prepareUs), formatDuration(evaluation.unknownUs)), style = ComposeTypography.bodyLarge)
                evaluation.items.forEach { item ->
                    Text(if (item.validUs == 0L) stringResource(R.string.result_item_unknown, eventTitle(item.kind.name))
                    else stringResource(R.string.result_item, eventTitle(item.kind.name), item.count, formatDuration(item.abnormalUs), formatDuration(item.validUs)), style = ComposeTypography.bodyMedium)
                }
                Text(stringResource(R.string.result_away_count, evaluation.awayCount, evaluation.timeoutAwayCount), style = ComposeTypography.bodyMedium)
            }
            if (media?.saveState == SaveState.Saved.name) Button(onPlayback) { Text(stringResource(R.string.view_video), style = ComposeTypography.buttonLabelLarge) }
            if (media?.saveState != SaveState.Saved.name) TextButton(onPlayback) { Text(stringResource(R.string.timeline_title), style = ComposeTypography.buttonLabelLarge) }
            if (media?.saveState in listOf(SaveState.Finalizing.name, SaveState.RetryableFailure.name)) {
                Button({ viewModel.retry(id) }, enabled = !state.busy) { Text(stringResource(R.string.retry_save), style = ComposeTypography.buttonLabelLarge) }
                TextButton(onCleanup, enabled = !state.busy) { Text(stringResource(R.string.cleanup), style = ComposeTypography.buttonLabelLarge) }
            }
            if (media?.saveState == SaveState.Unrecoverable.name) Button({ viewModel.keep(id, onDone) }, enabled = !state.busy) {
                Text(stringResource(R.string.keep_statistics), style = ComposeTypography.buttonLabelLarge)
            }
            if (media?.saveState == SaveState.Saved.name) TextButton({ purging = true }, enabled = !state.busy) { Text(stringResource(R.string.purge_video), style = ComposeTypography.buttonLabelLarge) }
            TextButton({ deleting = true }, enabled = !state.busy) { Text(stringResource(R.string.discard_record), style = ComposeTypography.buttonLabelLarge) }
        }
        if (state.failed) TextButton({ viewModel.loadResult(id) }, enabled = !state.busy) { Text(stringResource(R.string.retry), style = ComposeTypography.buttonLabelLarge) }
        Button(onDone) { Text(stringResource(R.string.done), style = ComposeTypography.buttonLabelLarge) }
    }
    if (editing) state.result?.let { result -> RecordNoteDialog(result.session.title, result.session.note, state.busy, state.operationFailed,
        { editing = false }, { title, note -> viewModel.saveNote(id, title, note) { editing = false } }) }
    if (purging) PurgeConfirmation({ purging = false }, { purging = false; viewModel.purge(setOf(id), null) })
    if (deleting) DeleteConfirmation(onCancel = { deleting = false }, onConfirm = {
        deleting = false
        viewModel.delete(setOf(id), null, onDeleted)
    })
}

@Composable
fun AlbumScreen(viewModel: RecordViewModel, app: MonitorApplication, cleanup: Boolean, protectedId: String?,
    onOpen: (String, Boolean) -> Unit, onDone: () -> Unit, onStatistics: () -> Unit, onDeleted: (Set<String>) -> Unit,
    modifier: Modifier = Modifier) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    var purging by remember { mutableStateOf(false) }
    var filtering by remember { mutableStateOf(false) }
    var management by rememberSaveable { mutableStateOf(cleanup) }
    var deleting by remember { mutableStateOf(false) }
    val grid = rememberLazyGridState()
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, protectedId) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.runtime.records.revisionFlow.collect { viewModel.refreshAlbum(protectedId) }
        }
    }
    Column(modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        Text(stringResource(if (cleanup) R.string.cleanup else R.string.album_title), style = ComposeTypography.headlineSmall)
        Row { TextButton({ filtering = true }, enabled = !state.busy) { Text(stringResource(R.string.record_filter), style = ComposeTypography.buttonLabelLarge) }
            TextButton(onStatistics) { Text(stringResource(R.string.statistics_title), style = ComposeTypography.buttonLabelLarge) } }
        if (state.filter != childmonitor.model.RecordFilter()) Text(stringResource(R.string.filter_active), style = ComposeTypography.labelSmall)
        Row {
            TextButton({ management = !management; if (!management) viewModel.clearSelection() }) { Text(stringResource(if (management) R.string.cancel else R.string.manage), style = ComposeTypography.buttonLabelLarge) }
            if (management) TextButton({ viewModel.selectAll(protectedId) }, enabled = !state.busy) { Text(stringResource(R.string.select_all), style = ComposeTypography.buttonLabelLarge) }
            TextButton({ viewModel.refreshAlbum(protectedId) }, enabled = !state.busy) { Text(stringResource(R.string.refresh), style = ComposeTypography.buttonLabelLarge) }
        }
        if (state.failed || state.operationFailed) Text(stringResource(R.string.operation_failed), style = ComposeTypography.bodyMedium)
        if (state.loaded && state.items.isEmpty()) Text(stringResource(R.string.album_empty), style = ComposeTypography.bodyLarge)
        LazyVerticalGrid(GridCells.Fixed(3), Modifier.weight(1f), state = grid,
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.items.groupBy { it.session.localDate }.forEach { (date, entries) ->
                item(key = "date:$date", span = { GridItemSpan(maxLineSpan) }) { Text(date, style = ComposeTypography.titleMedium) }
                items(entries, key = { it.session.id }) { entry ->
                    val id = entry.session.id
                    val protected = id == protectedId
                    Card(Modifier.fillMaxWidth().combinedClickable(
                        onClick = { if (management) { if (!protected) viewModel.select(id) } else onOpen(id, entry.media?.saveState == SaveState.Saved.name) },
                        onLongClick = { management = true; if (!protected) viewModel.select(id) })) {
                        Thumbnail(app, entry.media?.relativePath, id, entry.media?.saveState == SaveState.Saved.name)
                        if (entry.session.title.isNotBlank()) Text(entry.session.title, style = ComposeTypography.labelMedium, maxLines = 1)
                        Text(formatDuration(entry.session.durationUs), style = ComposeTypography.labelMedium, modifier = Modifier.padding(6.dp))
                        Text(if (protected) stringResource(R.string.protected_record) else saveStateText(entry.media?.saveState), style = ComposeTypography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp))
                        if (management) Checkbox(id in state.selected, { if (!protected) viewModel.select(id) }, enabled = !protected && !state.busy)
                    }
                }
            }
            if (state.cursor != null) item(span = { GridItemSpan(maxLineSpan) }) {
                TextButton({ viewModel.loadAlbum() }, enabled = !state.busy) { Text(stringResource(R.string.load_more), style = ComposeTypography.buttonLabelLarge) }
            }
        }
        if (management) TextButton({ purging = true }, enabled = state.selected.isNotEmpty() && !state.busy) { Text(stringResource(R.string.purge_video), style = ComposeTypography.buttonLabelLarge) }
        if (management) Button({ deleting = true }, enabled = state.selected.isNotEmpty() && !state.busy) {
            Text(stringResource(R.string.delete_count, state.selected.size), style = ComposeTypography.buttonLabelLarge)
        }
        TextButton(onDone) { Text(stringResource(if (cleanup) R.string.cleanup_done else R.string.back_home), style = ComposeTypography.buttonLabelLarge) }
    }
    if (filtering) RecordFilterDialog(state.filter, { filtering = false }, { viewModel.applyFilter(it); filtering = false })
    if (purging) PurgeConfirmation({ purging = false }, { purging = false; viewModel.purge(state.selected, protectedId) })
    if (deleting) DeleteConfirmation(onCancel = { deleting = false }, onConfirm = {
        deleting = false
        viewModel.delete(state.selected, protectedId, onDeleted)
    })
}

@Composable
fun Thumbnail(app: MonitorApplication, relativePath: String?, id: String, saved: Boolean, modifier: Modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3), placeholder: @Composable () -> Unit = {}) {
    val bitmap by produceState<Bitmap?>(null, relativePath, saved) {
        value = null
        if (relativePath == null || !saved) return@produceState
        value = withContext(Dispatchers.IO) {
            val dir = File(app.cacheDir, "child-monitor-thumbnails").apply { mkdirs() }
            val thumbnail = File(dir, "$id.jpg")
            if (thumbnail.isFile) BitmapFactory.decodeFile(thumbnail.absolutePath)
            else {
                val reader = MediaMetadataRetriever()
                try {
                    reader.setDataSource(app.persistence.files.resolve(relativePath).absolutePath)
                    (if (android.os.Build.VERSION.SDK_INT >= 27) reader.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 240, 180)
                    else reader.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC))?.also { image ->
                        thumbnail.outputStream().use { image.compress(Bitmap.CompressFormat.JPEG, 80, it) }
                    }
                } catch (_: Exception) { null }
                finally { reader.release() }
            }
        }
    }
    val image = if (relativePath != null && saved) bitmap else null
    if (image != null) Image(image.asImageBitmap(), null, modifier, contentScale = ContentScale.Crop)
    else Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = androidx.compose.ui.Alignment.Center) { placeholder() }
}

@Composable
fun DeleteConfirmation(onCancel: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onCancel, text = { Text(stringResource(R.string.delete_confirmation), style = ComposeTypography.bodyMedium) },
        confirmButton = { TextButton(onConfirm) { Text(stringResource(R.string.delete), style = ComposeTypography.buttonLabelLarge) } },
        dismissButton = { TextButton(onCancel) { Text(stringResource(R.string.cancel), style = ComposeTypography.buttonLabelLarge) } })
}

@Composable
fun saveStateText(state: String?) = stringResource(when (state) {
    "Saved" -> R.string.media_saved
    "Finalizing", "RetryableFailure" -> R.string.media_pending
    "MetadataOnly" -> R.string.statistics_only
    else -> R.string.media_unavailable
})

@Composable
fun eventTitle(kind: String) = stringResource(when (kind) {
    "HeadDown", "LeanForward" -> R.string.detection_head_down
    "HeadTilt" -> R.string.detection_head_tilt
    "BodyLean" -> R.string.detection_body_lean
    "Away" -> R.string.detection_away
    "Return" -> R.string.event_return
    "Rest" -> R.string.event_rest
    "Uncertain" -> R.string.monitor_unclear
    else -> R.string.event_interruption
})
fun endReasonText(reason: String) = when (reason) {
    "TargetReached" -> R.string.target_reached
    "SystemLocked" -> R.string.end_locked
    "Background" -> R.string.end_background
    "IneffectiveTimeout" -> R.string.end_unclear
    "PrepareTimeout" -> R.string.end_prepare
    "StorageLow" -> R.string.storage_low
    else -> R.string.end_capture
}

@Composable
private fun Celebration(modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(1800)) }
    Canvas(modifier) {
        val colors = listOf(Color(0xFFEE9F48), Color(0xFF6E9E86), Color(0xFF87B9D9))
        for (index in 0..35) {
            val p = progress.value
            val x = size.width * ((index * 37 % 101) / 100f)
            val y = size.height * p + ((index * 13 % 31) - 15)
            drawCircle(colors[index % colors.size].copy(alpha = 1f - p), 3.dp.toPx(), Offset(x, y))
        }
    }
}
