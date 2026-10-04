package childmonitor.ui

import android.Manifest
import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.RectF
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import childmonitor.android.R
import childmonitor.app.MonitorApplication
import childmonitor.model.*

@Composable
fun MonitorRoute(viewModel: MonitorViewModel, app: MonitorApplication, onOpenProbe: () -> Unit,
    onAlbum: () -> Unit, onStorage: () -> Unit, onAvatar: () -> Unit, onResult: (String, Boolean) -> Unit, onCleanup: () -> Unit,
    modifier: Modifier = Modifier) {
    val snapshot by viewModel.snapshotFlow.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val runtime = viewModel.runtime
    var panel by rememberSaveable { mutableStateOf(false) }
    var denied by rememberSaveable { mutableStateOf(false) }
    var gate by remember { mutableStateOf(false) }
    var confirmStop by remember { mutableStateOf(false) }
    var placementFailed by remember { mutableStateOf(false) }
    var roi by remember { mutableStateOf(RectF()) }
    val active = snapshot.runState in listOf(RunState.Starting, RunState.Preparing, RunState.Monitoring, RunState.Stopping)
    val preview = remember(context) { PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FIT_CENTER
    } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        if (granted && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) viewModel.command { runtime.start(it) }
    }
    DisposableEffect(owner, preview) {
        app.capture.attach(owner, preview)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                val locked = (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked
                viewModel.command { runtime.stop(it, if (locked) EndReason.SystemLocked else EndReason.Background) }
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            app.capture.detach(preview)
            viewModel.command { runtime.stop(it, EndReason.CameraInterrupted) }
        }
    }
    val activity = context as? Activity
    DisposableEffect(active, snapshot.darkened, activity) {
        val window = activity?.window
        val old = window?.attributes?.screenBrightness
        if (active) window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (snapshot.darkened) window?.attributes = window?.attributes?.apply { screenBrightness = .01f }
        onDispose {
            if (old != null) window?.attributes = window?.attributes?.apply { screenBrightness = old }
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    LaunchedEffect(snapshot.resultId, snapshot.captureReleased) {
        if (snapshot.captureReleased) snapshot.resultId?.let { id -> runtime.consumeResult(java.util.UUID.randomUUID().toString()); onResult(id, true) }
    }
    BackHandler(active && !panel) {
        if (snapshot.runState == RunState.Starting) viewModel.command { runtime.stop(it) } else confirmStop = true
    }
    fun guarded(action: () -> Unit) {
        if (active || !snapshot.captureReleased) gate = true else action()
    }
    Box(modifier.fillMaxSize().pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                if (event.changes.any { it.pressed && !it.previousPressed }) viewModel.command { runtime.interact(it) }
            }
        }
    }) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(20.dp)
            .then(if (panel) Modifier.clearAndSetSemantics {} else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MonitorToolbar(app, onAlbum = { guarded(onAlbum) }, onSettings = { panel = true })
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                AndroidView(factory = { preview }, modifier = Modifier.fillMaxSize())
                if (!active) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(painterResource(AvatarResources.drawable(snapshot.settings?.preferences?.avatarId ?: AvatarCatalog.defaultId)),
                            stringResource(R.string.avatar_title), Modifier.size(180.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape))
                        TextButton({ guarded(onAvatar) }) { Text(stringResource(R.string.avatar_edit), style = ComposeTypography.buttonLabelLarge) }
                    }
                }
                if (snapshot.runState == RunState.Preparing) {
                    Canvas(Modifier.fillMaxSize().pointerInput(snapshot.emptySeatReady) {
                        if (snapshot.emptySeatReady) return@pointerInput
                        var start = Offset.Zero
                        detectDragGestures(onDragStart = { start = it }, onDrag = { change, _ ->
                            change.consume()
                            val end = change.position
                            roi = RectF(if (start.x < end.x) start.x else end.x, if (start.y < end.y) start.y else end.y,
                                if (start.x > end.x) start.x else end.x, if (start.y > end.y) start.y else end.y)
                        })
                    }) {
                        if (!roi.isEmpty) drawRect(Color.Green, Offset(roi.left, roi.top), androidx.compose.ui.geometry.Size(roi.width(), roi.height()), style = Stroke(3.dp.toPx()))
                    }
                }
            }
            Text(stringResource(when (snapshot.runState) {
                RunState.Idle -> R.string.monitor_idle
                RunState.Starting -> R.string.monitor_starting
                RunState.Preparing -> R.string.monitor_preparing
                RunState.Monitoring -> if (snapshot.unclear) R.string.monitor_unclear else if (snapshot.away) R.string.monitor_away else R.string.monitor_observing
                RunState.Stopping -> R.string.monitor_stopping
                RunState.Stopped -> R.string.monitor_stopped
            }), style = ComposeTypography.titleMedium)
            if (snapshot.runState == RunState.Preparing) {
                PlacementGuide(snapshot)
                Text(stringResource(if (snapshot.emptySeatReady) R.string.placement_sit_down else R.string.placement_help), style = ComposeTypography.bodySmall)
                if (!snapshot.emptySeatReady) Button({
                    val region = app.capture.mapPlacement(roi)
                    placementFailed = region == null
                    if (region != null) viewModel.command { runtime.confirmPlacement(region, it) }
                }, enabled = !roi.isEmpty) {
                    Text(stringResource(R.string.placement_confirm), style = ComposeTypography.buttonLabelLarge)
                }
                else if (!snapshot.needsGuardian) TextButton({ panel = true }) {
                    Text(stringResource(R.string.reposition), style = ComposeTypography.buttonLabelLarge)
                }
            }
            if (placementFailed) Text(stringResource(R.string.placement_invalid), style = ComposeTypography.bodyMedium)
            if (snapshot.needsGuardian) {
                Text(stringResource(R.string.placement_required), style = ComposeTypography.bodyMedium)
                TextButton({ panel = true }) { Text(stringResource(R.string.reposition), style = ComposeTypography.buttonLabelLarge) }
            }
            snapshot.interruptionId?.let { id -> Row {
                TextButton({ onResult(id, false) }) { Text(stringResource(R.string.interruption_result), style = ComposeTypography.buttonLabelLarge) }
                TextButton({ viewModel.command { runtime.acknowledge(id, it) } }) { Text(stringResource(R.string.dismiss), style = ComposeTypography.buttonLabelLarge) }
            } }
            if (snapshot.error != null) {
                Text(stringResource(if (snapshot.error == ErrorCode.StorageLow) R.string.storage_low else R.string.operation_failed), style = ComposeTypography.bodyMedium)
            }
            if (snapshot.error == ErrorCode.StorageLow && !active) TextButton({ guarded(onCleanup) }) {
                Text(stringResource(R.string.cleanup), style = ComposeTypography.buttonLabelLarge)
            }
            snapshot.reminderId?.let { Text(stringResource(reminderText(it)), style = ComposeTypography.titleMedium) }
            if (!snapshot.captureReleased && !active) Text(stringResource(R.string.probe_release_pending), style = ComposeTypography.bodyMedium)
            if (!snapshot.ready) TextButton({ viewModel.command { runtime.initialize(it) } }) { Text(stringResource(R.string.retry), style = ComposeTypography.buttonLabelLarge) }
            if (denied) TextButton({ context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) {
                Text(stringResource(R.string.permission_settings), style = ComposeTypography.buttonLabelLarge)
            }
            Text(stringResource(R.string.monitor_duration, formatDuration(snapshot.durationUs)), style = ComposeTypography.titleMedium)
            val targetMs = snapshot.settings?.monitorSettings?.targetDurationMs ?: 0L
            if (targetMs > 0) Text(stringResource(if (snapshot.targetReached) R.string.target_reached else R.string.target_progress,
                formatDuration(if (targetMs * 1_000 > snapshot.durationUs) targetMs * 1_000 - snapshot.durationUs else 0)), style = ComposeTypography.bodyMedium)
            Button(onClick = {
                if (active) { if (snapshot.runState == RunState.Starting) viewModel.command { runtime.stop(it) } else confirmStop = true }
                else if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) viewModel.command { runtime.start(it) }
                else permission.launch(Manifest.permission.CAMERA)
            }, enabled = snapshot.ready && !snapshot.operationBusy && snapshot.runState != RunState.Stopping && (active || snapshot.captureReleased), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(if (snapshot.runState == RunState.Starting) R.string.start_cancel else if (active) R.string.monitor_stop else R.string.monitor_start), style = ComposeTypography.buttonTitleMedium)
            }
        }
        if (panel) {
            SettingsOverlay(onClose = { panel = false }) {
                ParentProtected(app, protected = true, onCancel = { panel = false }) {
                    SettingsScreen(viewModel, onAvatar = onAvatar, onBack = { panel = false }, onStorage = onStorage,
                        onOpenProbe = onOpenProbe,
                        onReposition = { panel = false; viewModel.command { runtime.reposition(it) } })
                }
            }
        }
        if (snapshot.darkened && !panel) Box(Modifier.fillMaxSize().background(Color.Black).clickable {
            panel = false
            viewModel.command { runtime.interact(it) }
        }, contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.monitor_dark), color = Color.DarkGray, style = ComposeTypography.bodyMedium)
        }
        if (confirmStop) AlertDialog(onDismissRequest = { confirmStop = false },
            text = { Text(stringResource(R.string.stop_confirm), style = ComposeTypography.bodyMedium) },
            confirmButton = { TextButton({ confirmStop = false; viewModel.command { runtime.stop(it) } }) { Text(stringResource(R.string.monitor_stop), style = ComposeTypography.buttonLabelLarge) } },
            dismissButton = { TextButton({ confirmStop = false }) { Text(stringResource(R.string.cancel), style = ComposeTypography.buttonLabelLarge) } })
        if (gate) AlertDialog(onDismissRequest = { gate = false }, text = { Text(stringResource(R.string.navigation_guard), style = ComposeTypography.bodyMedium) },
            confirmButton = { TextButton({ gate = false }) { Text(stringResource(R.string.understood), style = ComposeTypography.buttonLabelLarge) } })
    }
}

fun formatDuration(us: Long): String {
    val seconds = us / 1_000_000
    return "%02d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
}

@Composable
fun MonitorScreen(snapshot: UiSnapshot, onRefreshStorage: () -> Unit, onOpenProbe: () -> Unit,
    modifier: Modifier = Modifier, showProbe: Boolean = true) {
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(AvatarResources.drawable(AvatarCatalog.defaultId)), null, Modifier.size(180.dp))
        Text(stringResource(R.string.monitor_idle), style = ComposeTypography.titleLarge)
        Text(stringResource(R.string.monitor_duration, formatDuration(snapshot.durationUs)), style = ComposeTypography.bodyLarge)
    }
}
@Preview(showBackground = true)
@Composable
private fun MonitorScreenPreview() { AppPreview { MonitorScreen(UiSnapshot(), {}, {}) } }

fun reminderText(kind: String) = when (kind) {
    "reminder_head_down" -> R.string.reminder_head_down
    "reminder_head_tilt" -> R.string.reminder_head_tilt
    "reminder_body_lean" -> R.string.reminder_body_lean
    "reminder_posture" -> R.string.reminder_posture
    "reminder_away" -> R.string.reminder_away
    "reminder_rest" -> R.string.reminder_rest
    else -> R.string.reminder_unclear
}
