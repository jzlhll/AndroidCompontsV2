package childmonitor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import childmonitor.android.R
import childmonitor.app.MonitorApplication
import childmonitor.model.SaveState
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.delay

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlaybackScreen(id: String, viewModel: RecordViewModel, app: MonitorApplication, onBack: () -> Unit,
    onResult: () -> Unit, onDeleted: (Set<String>) -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val result = state.result
    LaunchedEffect(state.loaded, result) { if (state.loaded && result == null && !state.failed) onDeleted(setOf(id)) }
    val events = remember(result) {
        (result?.events.orEmpty().map { TimelineItem(it.id, it.kind, it.startUs, it.endUs) } +
            result?.rest.orEmpty().map { TimelineItem("rest:${it.id}", "Rest", it.startUs, it.endUs) })
            .sortedWith(compareBy<TimelineItem> { it.startUs }.thenBy { it.id })
    }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var position by remember { mutableLongStateOf(0) }
    var duration by remember { mutableLongStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var annotationEvent by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf<String?>(null) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, id) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.runtime.records.revisionFlow.collect { viewModel.loadResult(id) }
        }
    }
    LaunchedEffect(result?.media, attempt) {
        val media = result?.media ?: return@LaunchedEffect
        if (media.saveState != SaveState.Saved.name) return@LaunchedEffect
        if (app.persistence.files.probe(media.relativePath) == null) { failed = true; return@LaunchedEffect }
        failed = false
        player = ExoPlayer.Builder(app).build().apply {
            setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(app.persistence.files.resolve(media.relativePath))))
            prepare()
        }
    }
    val instance = remember(player) { UUID.randomUUID().toString() }
    DisposableEffect(player, owner) {
        val current = player
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) current?.pause() }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlayerError(error: PlaybackException) { failed = true }
        }
        if (current != null) app.playbackRegistry.register(instance, id, current)
        current?.addListener(listener)
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            current?.removeListener(listener)
            app.playbackRegistry.remove(instance)
        }
    }
    LaunchedEffect(player) {
        val current = player ?: return@LaunchedEffect
        while (true) {
            position = current.currentPosition
            duration = if (current.duration > 0) current.duration else 0
            delay(250.milliseconds)
        }
    }
    Column(modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.playback_title), style = ComposeTypography.headlineSmall)
        val current = player
        if (current != null && !failed) {
            PlayerSurface(player = current, modifier = Modifier.fillMaxWidth().height(280.dp))
            Row {
                Button({ if (current.isPlaying) current.pause() else current.play() }) {
                    Text(stringResource(if (playing) R.string.pause else R.string.play), style = ComposeTypography.buttonLabelLarge)
                }
                Text(formatDuration(position * 1_000), style = ComposeTypography.bodyMedium, modifier = Modifier.padding(12.dp))
            }
            val displayed = when { position < 0 -> 0L; position > duration -> duration; else -> position }
            if (duration > 0) Slider(displayed.toFloat(), onValueChange = { current.seekTo(it.toLong()) }, valueRange = 0f..duration.toFloat())
        }
        if (failed || state.failed) {
            Text(stringResource(R.string.playback_failed), style = ComposeTypography.bodyMedium)
            TextButton({
                if (state.failed) viewModel.loadResult(id)
                else { app.playbackRegistry.remove(instance); player = null; attempt++ }
            }, enabled = !state.busy) { Text(stringResource(R.string.retry), style = ComposeTypography.buttonLabelLarge) }
        }
        if (result?.media?.saveState != SaveState.Saved.name) Text(saveStateText(result?.media?.saveState), style = ComposeTypography.bodyMedium)
        TextButton(onResult) { Text(stringResource(R.string.result_title), style = ComposeTypography.buttonLabelLarge) }
        Text(stringResource(R.string.timeline_title), style = ComposeTypography.titleMedium)
        val filtered = events.filter { filter == null || it.kind == filter }
        if (result != null && result.session.durationUs > 0) Canvas(Modifier.fillMaxWidth().height(48.dp)) {
            val total = result.session.durationUs.toFloat()
            drawLine(Color.Gray, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx())
            filtered.forEach { event ->
                val start = event.startUs / total * size.width
                val end = event.endUs / total * size.width
                val color = when (event.kind) { "Rest" -> Color(0xFF6E9E86); "Uncertain", "Interruption" -> Color.Gray; else -> Color(0xFFDD9854) }
                drawLine(color, Offset(start, size.height / 2), Offset(if (end - start > 2.dp.toPx()) end else start + 2.dp.toPx(), size.height / 2), 12.dp.toPx())
            }
            val head = position * 1_000 / total * size.width
            drawLine(Color(0xFF285B83), Offset(head, 0f), Offset(head, size.height), 2.dp.toPx())
        }
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            TextButton({ filter = null }) { Text(stringResource(R.string.all_events), style = ComposeTypography.buttonLabelLarge) }
            events.map { it.kind }.distinct().forEach { kind ->
                TextButton({ filter = kind }) { Text(eventTitle(kind), style = ComposeTypography.buttonLabelMedium) }
            }
        }
        filtered.forEach { event ->
            val offsetUs = event.startUs - (result?.media?.videoOriginOffsetUs ?: 0)
            val seekable = current != null && offsetUs >= 0 && offsetUs / 1_000 <= duration && !failed
            Text(stringResource(R.string.event_row, eventTitle(event.kind), formatDuration(event.startUs)),
                style = ComposeTypography.bodyLarge, modifier = Modifier.fillMaxWidth().clickable(enabled = seekable) { current?.seekTo(offsetUs / 1_000) }.padding(vertical = 8.dp))
            if (result?.events?.any { it.id == event.id } == true) {
                val annotation = result.annotations.firstOrNull { it.eventId == event.id }
                TextButton({ annotationEvent = event.id }, enabled = !state.busy) {
                    Text(stringResource(when (annotation?.label) { "false_positive" -> R.string.annotation_false; "uncertain" -> R.string.annotation_uncertain; else -> R.string.annotation_add }), style = ComposeTypography.buttonLabelLarge)
                }
            }
            if (!seekable && duration > 0) Text(stringResource(R.string.timeline_outside), style = ComposeTypography.labelSmall)
        }
        TextButton({ deleting = true }, enabled = !state.busy) { Text(stringResource(R.string.delete), style = ComposeTypography.buttonLabelLarge) }
        TextButton(onBack) { Text(stringResource(R.string.back), style = ComposeTypography.buttonLabelLarge) }
    }
    annotationEvent?.let { eventId ->
        val annotation = result?.annotations?.firstOrNull { it.eventId == eventId }
        EventAnnotationDialog(annotation, state.busy, state.operationFailed, { annotationEvent = null },
            { label, note -> viewModel.annotate(eventId, label, note) { annotationEvent = null } })
    }
    if (deleting) DeleteConfirmation(onCancel = { deleting = false }, onConfirm = {
        deleting = false
        player?.pause()
        app.playbackRegistry.remove(instance)
        player = null
        viewModel.delete(setOf(id), null, onDeleted)
    })
}

private data class TimelineItem(val id: String, val kind: String, val startUs: Long, val endUs: Long)
