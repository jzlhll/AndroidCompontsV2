package childmonitor.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import childmonitor.android.R
import childmonitor.platform.ProbeError
import childmonitor.platform.ProbePhase
import childmonitor.platform.ProbeState

@Composable
fun ProbeRoute(viewModel: ProbeViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val probe = viewModel.probe
    val state by probe.stateFlow.collectAsStateWithLifecycle()
    var permissionDenied by remember { mutableStateOf(false) }
    val preview = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionDenied = !granted
        if (granted && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            probe.start(owner, preview.surfaceProvider, preview.display?.rotation ?: Surface.ROTATION_0)
        }
    }
    DisposableEffect(owner, probe, preview) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) probe.stop()
        }
        owner.lifecycle.addObserver(observer)
        preview.keepScreenOn = true
        onDispose {
            owner.lifecycle.removeObserver(observer)
            preview.keepScreenOn = false
            probe.stop()
        }
    }
    BackHandler(state.busy) { probe.stop() }
    ProbeScreen(
        state = state,
        permissionDenied = permissionDenied,
        onStart = { permission.launch(Manifest.permission.CAMERA) },
        onStop = probe::stop,
        onBack = onBack,
        onOpenSettings = {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        },
        modifier = modifier,
    ) {
        AndroidView(factory = { preview }, modifier = Modifier.fillMaxWidth().height(260.dp))
    }
}

@Composable
fun ProbeScreen(
    state: ProbeState,
    permissionDenied: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.probe_title), style = ComposeTypography.headlineSmall)
        Text(stringResource(R.string.probe_description), style = ComposeTypography.bodyMedium)
        content()
        Text(stringResource(when (state.phase) {
            ProbePhase.Idle -> R.string.probe_idle
            ProbePhase.Starting -> R.string.probe_starting
            ProbePhase.Recording -> R.string.probe_recording
            ProbePhase.Finishing -> R.string.probe_finishing
            ProbePhase.Complete -> R.string.probe_complete
            ProbePhase.Failed -> R.string.probe_failed
            ProbePhase.Canceled -> R.string.probe_canceled
        }), style = ComposeTypography.titleMedium)
        Text(stringResource(R.string.probe_frames, state.analysisFrames, state.poseFrames), style = ComposeTypography.bodyMedium)
        if (permissionDenied) {
            Text(stringResource(R.string.permission_denied), style = ComposeTypography.bodyMedium)
            OutlinedButton(onOpenSettings) {
                Text(stringResource(R.string.permission_settings), style = ComposeTypography.buttonLabelLarge)
            }
        }
        state.error?.let { error ->
            Text(stringResource(when (error) {
                ProbeError.PermissionDenied -> R.string.permission_denied
                ProbeError.CameraBusy -> R.string.probe_camera_busy
                ProbeError.NoFrontCamera -> R.string.probe_no_front_camera
                ProbeError.UnsupportedSize -> R.string.probe_unsupported_size
                ProbeError.ModelFailed -> R.string.probe_model_failed
                ProbeError.NoAnalysis -> R.string.probe_no_analysis
                ProbeError.FinalizeFailed -> R.string.probe_finalize_failed
                ProbeError.InvalidMedia -> R.string.probe_invalid_media
                ProbeError.Timeout -> R.string.probe_timeout
                ProbeError.CleanupFailed -> R.string.probe_cleanup_failed
                ProbeError.CaptureFailed -> R.string.probe_capture_failed
            }), style = ComposeTypography.bodyMedium)
        }
        if (!state.captureReleased && !state.busy) {
            Text(stringResource(R.string.probe_release_pending), style = ComposeTypography.bodyMedium)
        }
        state.report?.let { report ->
            Text(stringResource(R.string.probe_media, report.width, report.height,
                report.firstVideoPtsUs, report.lastVideoPtsUs), style = ComposeTypography.bodyMedium)
            Text(stringResource(R.string.probe_timestamps, report.firstAnalysisTimeNs,
                report.lastAnalysisTimeNs, report.startCallbackElapsedNs), style = ComposeTypography.bodySmall)
            Text(stringResource(R.string.probe_mapping_pending), style = ComposeTypography.bodyMedium)
        }
        if (state.busy) {
            Button(onStop, enabled = state.phase != ProbePhase.Finishing) {
                Text(stringResource(R.string.probe_cancel), style = ComposeTypography.buttonLabelLarge)
            }
        } else {
            Button(onStart, enabled = state.captureReleased) {
                Text(stringResource(R.string.probe_start), style = ComposeTypography.buttonLabelLarge)
            }
        }
        OutlinedButton(onBack, enabled = !state.busy) {
            Text(stringResource(R.string.back_home), style = ComposeTypography.buttonLabelLarge)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProbeScreenPreview() {
    AppPreview {
        ProbeScreen(ProbeState(), false, {}, {}, {}, {}) {
            Box(Modifier.fillMaxWidth().height(260.dp).background(Color.Black))
        }
    }
}
