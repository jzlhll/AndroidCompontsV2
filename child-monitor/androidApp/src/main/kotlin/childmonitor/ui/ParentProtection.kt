package childmonitor.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import childmonitor.android.R
import childmonitor.app.MonitorApplication
import childmonitor.data.database.entity.EventAnnotationEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 授权失效后移除受保护页面，释放播放器、弹窗及页面任务。 */
@Composable
fun ParentProtected(app: MonitorApplication, protected: Boolean, onCancel: () -> Unit,
    modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state by app.parentAccess.stateFlow.collectAsStateWithLifecycle()
    val authorized = !protected || state.ready && (!state.enabled || state.unlocked)
    LaunchedEffect(authorized) { if (authorized && protected) app.runtime.records.invalidateRecords() }
    Box(modifier) {
        if (authorized) content()
        else ParentUnlock(app, onCancel, Modifier.fillMaxSize())
    }
}

@Composable
private fun ParentUnlock(app: MonitorApplication, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val state by app.parentAccess.stateFlow.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val device = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) app.parentAccess.unlockWithDeviceCredential()
    }
    val session by app.runtime.snapshotFlow.collectAsStateWithLifecycle()
    val keyguard = remember(context) { context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager }
    BackHandler(onBack = onCancel)
    Column(modifier.safeDrawingPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.parent_title), style = ComposeTypography.titleLarge)
        if (state.ready) {
            PinField(pin, { pin = it }, R.string.parent_pin)
            Button({ scope.launch {
                busy = true; failed = false
                try { failed = !app.parentAccess.verify(pin); pin = "" }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { failed = true }
                finally { busy = false }
            } }, enabled = !busy && pin.length == 6) { Text(stringResource(R.string.parent_unlock), style = ComposeTypography.labelLarge) }
            if (keyguard.isDeviceSecure && session.runState in listOf(childmonitor.model.RunState.Idle, childmonitor.model.RunState.Stopped)) TextButton({
                val intent = keyguard.createConfirmDeviceCredentialIntent(context.getString(R.string.parent_title), context.getString(R.string.parent_device_prompt))
                if (intent != null) try { device.launch(intent) } catch (_: Exception) { failed = true }
            }, enabled = !busy) { Text(stringResource(R.string.parent_device), style = ComposeTypography.labelLarge) }
        }
        if (failed || state.failed) Text(stringResource(R.string.parent_failed), style = ComposeTypography.bodyMedium)
        if (state.failed) TextButton({ app.parentAccess.reload() }) { Text(stringResource(R.string.retry), style = ComposeTypography.labelLarge) }
        TextButton(onCancel) { Text(stringResource(R.string.back), style = ComposeTypography.labelLarge) }
    }
}

@Composable
fun ParentSettings(modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as MonitorApplication
    val state by app.parentAccess.stateFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    var invalid by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    fun save(value: String?) { scope.launch {
        busy = true
        try { app.parentAccess.configure(value); pin = ""; confirm = ""; failed = false }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { failed = true }
        finally { busy = false }
    } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.parent_set), style = ComposeTypography.titleMedium)
        Text(stringResource(R.string.parent_description), style = ComposeTypography.bodyMedium)
        PinField(pin, { pin = it }, R.string.parent_pin)
        PinField(confirm, { confirm = it }, R.string.parent_pin_confirm)
        Button({ invalid = pin.length != 6 || pin != confirm; if (!invalid) save(pin) }, enabled = !busy) { Text(stringResource(R.string.save), style = ComposeTypography.labelLarge) }
        if (invalid) Text(stringResource(R.string.parent_invalid), style = ComposeTypography.bodyMedium)
        if (failed) Text(stringResource(R.string.operation_failed), style = ComposeTypography.bodyMedium)
        if (state.enabled) TextButton({ removing = true }, enabled = !busy) { Text(stringResource(R.string.parent_disable), style = ComposeTypography.labelLarge) }
        Text(stringResource(R.string.parent_recovery), style = ComposeTypography.bodySmall)
    }
    if (removing) AlertDialog(onDismissRequest = { removing = false }, text = { Text(stringResource(R.string.parent_remove_confirm), style = ComposeTypography.bodyMedium) },
        confirmButton = { TextButton({ removing = false; save(null) }) { Text(stringResource(R.string.save), style = ComposeTypography.labelLarge) } },
        dismissButton = { TextButton({ removing = false }) { Text(stringResource(R.string.cancel), style = ComposeTypography.labelLarge) } })
}

@Composable
private fun PinField(value: String, onChange: (String) -> Unit, label: Int) {
    OutlinedTextField(value, { if (it.length <= 6 && it.all { char -> char in '0'..'9' }) onChange(it) },
        label = { Text(stringResource(label), style = ComposeTypography.labelMedium) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation())
}

@Composable
fun EventAnnotationDialog(annotation: EventAnnotationEntity?, busy: Boolean, failed: Boolean,
    onCancel: () -> Unit, onSave: (String?, String) -> Unit) {
    var label by remember { mutableStateOf(annotation?.label ?: "false_positive") }
    var note by remember { mutableStateOf(annotation?.note.orEmpty()) }
    AlertDialog(onDismissRequest = { if (!busy) onCancel() }, title = { Text(stringResource(R.string.annotation_add), style = ComposeTypography.titleMedium) }, text = {
        Column {
            Text(stringResource(R.string.annotation_explain), style = ComposeTypography.bodySmall)
            FilterChip(label == "false_positive", { label = "false_positive" }, label = { Text(stringResource(R.string.annotation_false), style = ComposeTypography.labelMedium) })
            FilterChip(label == "uncertain", { label = "uncertain" }, label = { Text(stringResource(R.string.annotation_uncertain), style = ComposeTypography.labelMedium) })
            OutlinedTextField(note, { if (it.length <= 500) note = it }, label = { Text(stringResource(R.string.annotation_note), style = ComposeTypography.labelMedium) })
            if (failed) Text(stringResource(R.string.operation_failed), style = ComposeTypography.bodyMedium)
            if (annotation != null) TextButton({ onSave(null, "") }, enabled = !busy) { Text(stringResource(R.string.annotation_remove), style = ComposeTypography.labelLarge) }
        }
    }, confirmButton = { TextButton({ onSave(label, note) }, enabled = !busy) { Text(stringResource(R.string.save), style = ComposeTypography.labelLarge) } },
        dismissButton = { TextButton(onCancel, enabled = !busy) { Text(stringResource(R.string.cancel), style = ComposeTypography.labelLarge) } })
}

@Preview(showBackground = true)
@Composable
private fun AnnotationPreview() { AppPreview { EventAnnotationDialog(null, false, false, {}, { _, _ -> }) } }
