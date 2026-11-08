package com.au.agit.ui

import com.au.agit.ui.components.AGitButton as Button
import com.au.agit.ui.components.AGitTextButton as TextButton
import com.au.agit.R
import androidx.compose.ui.res.stringResource
import android.os.SystemClock
import android.view.ViewGroup
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.au.agit.model.*
import com.au.agit.data.AGitErrorCode
import com.au.agit.data.Notice
import com.au.agit.errorStringResource
import com.au.agit.ui.theme.AGitTheme
import com.au.agit.ui.components.formatFileSize
import com.au.module_androiduiex.preview.AppPreview
import com.au.module_androiduiex.styles.ComposeColors
import com.au.module_androiduiex.styles.ComposeTypography
import com.au.module_androidui.toast.ToastBuilder
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun NoticeHost(notice: Notice?, onConsumed: (String) -> Unit) {
    val view = LocalView.current
    LaunchedEffect(notice?.id) {
        if (notice != null) {
            ToastBuilder().setOnViewGroup(view.rootView as? ViewGroup).setMessage(notice.message).toast()
            onConsumed(notice.id)
        }
    }
}

@Composable
fun OperationDialog(
    state: OperationState,
    notice: Notice?,
    onNoticeConsumed: (String) -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit,
    onPush: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)) {
        NoticeHost(notice, onNoticeConsumed)
        Surface(modifier = modifier.padding(20.dp).widthIn(max = 640.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Color(0xFF101914), contentColor = Color(0xFFE0EDE4)) {
            Column(Modifier.padding(20.dp).animateContentSize()) {
                Text(state.title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                val scroll = rememberLazyListState()
                val current = state.steps.indexOfLast { it.status != StepStatus.Pending }
                LaunchedEffect(current, state.steps.size, state.preview) {
                    if (state.preview != null) scroll.animateScrollToItem(state.steps.size)
                    else if (current >= 0) scroll.animateScrollToItem(current)
                }
                LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 320.dp), state = scroll, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    itemsIndexed(state.steps) { _, step -> TerminalStep(step) }
                    state.preview?.let { plan ->
                        item {
                            Text("${plan.projectName}\n${plan.branch} → origin/${plan.target.removePrefix("refs/heads/")}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                            if (plan.dirty) Text(stringResource(R.string.uncommitted_local), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
                            plan.commits.forEach { commit -> Text("${commit.id.take(8)} ${commit.message}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                            if (plan.commits.isEmpty()) Text(stringResource(R.string.no_outgoing), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                state.result?.let { Text(it, color = if (state.status in listOf(OperationStatus.Failed, OperationStatus.Blocked)) Color(0xFFFFBD9D) else Color(0xFF85E0AD), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 16.dp)) }
                if (state.cancelling) Text(stringResource(R.string.waiting_stop), modifier = Modifier.padding(top = 12.dp))
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                    if (state.status == OperationStatus.Running) {
                        if (state.cancellable && !state.cancelling) TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                    } else {
                        TextButton(onClick = onClose) { Text(if (state.status == OperationStatus.Success && state.preview == null) stringResource(R.string.done) else stringResource(R.string.close)) }
                    }
                }
                if (state.status != OperationStatus.Running && state.preview != null) {
                    if (state.allowTemporaryBranch) {
                        Text(stringResource(R.string.temporary_hint), style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { onPush(true) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(stringResource(R.string.temporary_push)) }
                    } else if (state.preview.commits.isNotEmpty()) {
                        Button(onClick = { onPush(false) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.confirm_push, state.preview.commits.size)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TerminalStep(step: OperationStep) {
    val running = step.status == StepStatus.Running
    var typed by remember(step.startedAt) { mutableIntStateOf(0) }
    var tick by remember(step.startedAt) { mutableIntStateOf(0) }
    var now by remember(step.startedAt) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(running, step.command) {
        if (running) {
            while (typed < step.command.length) { typed++; delay(14.milliseconds) }
        } else typed = step.command.length
    }
    LaunchedEffect(running) {
        while (running) { tick++; now = SystemClock.elapsedRealtime(); delay(450.milliseconds) }
    }
    val command = if (running) step.command.take(typed) else step.command
    val prefix = when (step.status) {
        StepStatus.Success -> "✓"
        StepStatus.Failed -> "!"
        StepStatus.Cancelled -> "×"
        StepStatus.Skipped -> "−"
        else -> "$"
    }
    val color = ComposeColors.TextLightDesc
    Column {
        Text("$prefix $command${if (running && tick % 2 == 0) " ▍" else ""}", color = color, fontFamily = FontFamily.Monospace, style = ComposeTypography.Font14sp)
        if (running) {
            val seconds = (now - (step.startedAt ?: now)) / 1000
            Text(stringResource(R.string.waiting_result, ".".repeat(tick % 3 + 1), seconds), color = color, fontFamily = FontFamily.Monospace, style = ComposeTypography.Font12sp)
        } else if (step.result != null) {
            Text(step.result, color = color, style = ComposeTypography.Font12sp, modifier = Modifier.padding(top = 4.dp))
        } else if (step.status == StepStatus.Skipped) {
            Text(stringResource(R.string.not_executed), color = color, style = ComposeTypography.Font12sp)
        }
        step.receivedBytes?.let { bytes ->
            Text(stringResource(R.string.received_size, formatFileSize(bytes)), color = color,
                fontFamily = FontFamily.Monospace, style = ComposeTypography.Font12sp)
        }
    }
}

@Preview
@Composable
private fun TerminalStepPreview() {
    AppPreview {
        MaterialTheme {
            Column(Modifier.background(Color(0xFF101914)).padding(24.dp)) {
                TerminalStep(OperationStep("git fetch origin", StepStatus.Success, stringResource(R.string.fetch_preview)))
            }
        }
    }
}

@Preview(locale = "en")
@Preview(locale = "zh-rCN")
@Composable
private fun OperationErrorPreview() {
    AppPreview {
        AGitTheme(AppTheme.Black) {
            val error = errorStringResource(AGitErrorCode.ENGINE_UNAVAILABLE)
            OperationDialog(
                state = OperationState(null, stringResource(R.string.clone_title),
                    listOf(OperationStep("git clone https://github.com/owner/repository.git", StepStatus.Failed, error)),
                    status = OperationStatus.Failed, result = error, kind = "clone"),
                notice = null, onNoticeConsumed = {}, onCancel = {}, onClose = {}, onPush = {},
            )
        }
    }
}
