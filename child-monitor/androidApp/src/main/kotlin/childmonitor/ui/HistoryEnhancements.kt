package childmonitor.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import childmonitor.android.R
import childmonitor.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.collect

@Composable
fun RecordNoteDialog(initialTitle: String, initialNote: String, busy: Boolean, failed: Boolean, onCancel: () -> Unit, onSave: (String, String) -> Unit) {
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var note by rememberSaveable { mutableStateOf(initialNote) }
    AlertDialog(onDismissRequest = { if (!busy) onCancel() }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (failed) Text(stringResource(R.string.operation_failed), style = ComposeTypography.bodyMedium)
            OutlinedTextField(title, { if (it.length <= 80) title = it }, label = { Text(stringResource(R.string.record_name), style = ComposeTypography.labelMedium) }, singleLine = true)
            OutlinedTextField(note, { if (it.length <= 1000) note = it }, label = { Text(stringResource(R.string.record_note), style = ComposeTypography.labelMedium) })
        }
    }, confirmButton = { TextButton({ onSave(title, note) }, enabled = !busy) { Text(stringResource(R.string.save), style = ComposeTypography.labelLarge) } },
        dismissButton = { TextButton(onCancel, enabled = !busy) { Text(stringResource(R.string.cancel), style = ComposeTypography.labelLarge) } })
}

private enum class DateBoundary { Start, End }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordFilterDialog(filter: RecordFilter, onCancel: () -> Unit, onApply: (RecordFilter) -> Unit) {
    var from by rememberSaveable { mutableStateOf(filter.fromDate.orEmpty()) }
    var to by rememberSaveable { mutableStateOf(filter.toDate.orEmpty()) }
    var query by rememberSaveable { mutableStateOf(filter.query) }
    var status by rememberSaveable { mutableStateOf(filter.status) }
    var calendar by remember { mutableStateOf<DateBoundary?>(null) }
    AlertDialog(onDismissRequest = onCancel, title = { Text(stringResource(R.string.record_filter), style = ComposeTypography.titleMedium) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            OutlinedTextField(query, { if (it.length <= 100) query = it }, label = { Text(stringResource(R.string.filter_query), style = ComposeTypography.labelMedium) })
            OutlinedButton({ calendar = DateBoundary.Start }) {
                Column {
                    Text(stringResource(R.string.filter_from), style = ComposeTypography.labelMedium)
                    Text(from.ifEmpty { stringResource(R.string.filter_no_date) }, style = ComposeTypography.bodyMedium)
                }
            }
            OutlinedButton({ calendar = DateBoundary.End }) {
                Column {
                    Text(stringResource(R.string.filter_to), style = ComposeTypography.labelMedium)
                    Text(to.ifEmpty { stringResource(R.string.filter_no_date) }, style = ComposeTypography.bodyMedium)
                }
            }
            listOf("all" to R.string.all_events, "normal" to R.string.filter_normal, "interrupted" to R.string.filter_interrupted,
                "pending" to R.string.filter_pending, "metadata" to R.string.statistics_only).forEach { (id, label) ->
                FilterChip(status == id, { status = id }, label = { Text(stringResource(label), style = ComposeTypography.labelMedium) })
            }
            TextButton({ onApply(RecordFilter()) }) { Text(stringResource(R.string.filter_clear), style = ComposeTypography.labelLarge) }
        }
    }, confirmButton = { TextButton({
        onApply(RecordFilter(from.ifEmpty { null }, to.ifEmpty { null }, status, query.trim()))
    }) { Text(stringResource(R.string.save), style = ComposeTypography.labelLarge) } },
        dismissButton = { TextButton(onCancel) { Text(stringResource(R.string.cancel), style = ComposeTypography.labelLarge) } })
    calendar?.let { boundary ->
        val initial = if (boundary == DateBoundary.Start) from else to
        val dates = rememberDatePickerState(
            initialSelectedDateMillis = initial.takeIf { it.isNotEmpty() }?.let { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() },
            selectableDates = remember(from, to, boundary) {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                        val date = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().toString()
                        return if (boundary == DateBoundary.Start) to.isEmpty() || date <= to else from.isEmpty() || date >= from
                    }
                }
            })
        DatePickerDialog(onDismissRequest = { calendar = null }, confirmButton = {
            TextButton({
                dates.selectedDateMillis?.let {
                    val date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    if (boundary == DateBoundary.Start) from = date else to = date
                }
                calendar = null
            }, enabled = dates.selectedDateMillis != null) { Text(stringResource(R.string.save), style = ComposeTypography.labelLarge) }
        }, dismissButton = {
            Row {
                TextButton({
                    if (boundary == DateBoundary.Start) from = "" else to = ""
                    calendar = null
                }) { Text(stringResource(R.string.filter_clear_date), style = ComposeTypography.labelLarge) }
                TextButton({ calendar = null }) { Text(stringResource(R.string.cancel), style = ComposeTypography.labelLarge) }
            }
        }) {
            DatePicker(state = dates, showModeToggle = false)
        }
    }
}

@Composable
fun StatisticsScreen(viewModel: RecordViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    var days by rememberSaveable { mutableIntStateOf(7) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, days) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.runtime.records.revisionFlow.collect {
                val today = LocalDate.now()
                viewModel.loadStatistics(today.minusDays(days - 1L).toString(), today.toString())
            }
        }
    }
    Column(modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.statistics_title), style = ComposeTypography.headlineSmall)
        Row { listOf(7, 30, 90).forEach { value -> FilterChip(days == value, { days = value },
            label = { Text(stringResource(R.string.statistics_range, value), style = ComposeTypography.labelMedium) }) } }
        Text(stringResource(R.string.statistics_explain), style = ComposeTypography.bodySmall)
        if (state.failed) {
            Text(stringResource(R.string.operation_failed), style = ComposeTypography.bodyMedium)
            TextButton({ val today = LocalDate.now(); viewModel.loadStatistics(today.minusDays(days - 1L).toString(), today.toString()) }) {
                Text(stringResource(R.string.retry), style = ComposeTypography.labelLarge)
            }
        }
        if (!state.busy && state.statistics.isEmpty()) Text(stringResource(R.string.statistics_no_data), style = ComposeTypography.bodyMedium)
        state.statistics.forEach { DailyStatisticsCard(it) }
        TextButton(onBack) { Text(stringResource(R.string.back), style = ComposeTypography.labelLarge) }
    }
}

@Composable
private fun DailyStatisticsCard(value: DailyStatistics, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.statistics_day, value.date, value.sessions, formatDuration(value.durationUs)), style = ComposeTypography.titleMedium)
            val ratio = if (value.durationUs > 0) value.observedUs.toDouble() / value.durationUs else 0.0
            Text(stringResource(R.string.statistics_coverage, (ratio * 100).toInt(), formatDuration(value.seatedUs), value.awayCount), style = ComposeTypography.bodyMedium)
            value.items.forEach { item ->
                if (item.validUs >= 600_000_000L) {
                    val fraction = item.abnormalUs.toFloat() / item.validUs
                    Text(stringResource(R.string.statistics_item, eventTitle(item.kind.name), (fraction * 100).toInt(), formatDuration(item.validUs)), style = ComposeTypography.bodyMedium)
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                } else Text(stringResource(R.string.result_item_unknown, eventTitle(item.kind.name)), style = ComposeTypography.labelSmall)
            }
            if (value.items.any { it.validUs < 600_000_000L }) Text(stringResource(R.string.statistics_insufficient), style = ComposeTypography.labelSmall)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StatisticsPreview() { AppPreview { DailyStatisticsCard(DailyStatistics("2026-10-02", 2, 1_800_000_000, 1_500_000_000, 1_400_000_000, 1,
    listOf(ItemStatistics(EventKind.HeadDown, 2, 20_000_000, 1_200_000_000)))) } }
