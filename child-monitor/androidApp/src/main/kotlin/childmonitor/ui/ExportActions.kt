package childmonitor.ui

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import childmonitor.android.R
import childmonitor.app.MonitorApplication
import childmonitor.data.repository.MonitorRepository
import childmonitor.model.SaveState
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 仅在用户操作后向指定文档或临时只读分享 URI 导出，不暴露应用私有录像目录。 */
@Composable
fun ExportActions(result: MonitorRepository.Result, exporting: Boolean, onExport: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as MonitorApplication
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<Int?>(null) }
    Column(modifier) {
        Text(stringResource(R.string.export_notice), style = ComposeTypography.bodySmall)
        if (result.media?.saveState == SaveState.Saved.name) TextButton(onExport, enabled = !busy && !exporting) {
            Text(stringResource(R.string.export_video), style = ComposeTypography.labelLarge)
        }
        TextButton({ scope.launch {
            busy = true; status = null
            try {
                val session = result.session
                val evaluation = result.evaluation
                val lines = buildList {
                    add(context.getString(R.string.report_title))
                    add(context.getString(R.string.report_date, session.localDate))
                    add(context.getString(R.string.report_duration, formatDuration(session.durationUs)))
                    if (evaluation != null) {
                        add(context.getString(R.string.report_seated, formatDuration(evaluation.seatedUs)))
                        add(context.getString(R.string.report_away, evaluation.awayCount))
                        evaluation.items.forEach { item ->
                            val title = context.getString(when (item.kind.name) {
                                "HeadDown" -> R.string.detection_head_down
                                "HeadTilt" -> R.string.detection_head_tilt
                                else -> R.string.detection_body_lean
                            })
                            add(if (item.validUs > 0) context.getString(R.string.result_item, title, item.count, formatDuration(item.abnormalUs), formatDuration(item.validUs))
                                else context.getString(R.string.result_item_unknown, title))
                        }
                        if (evaluation.partialData) add(context.getString(R.string.report_partial))
                    } else add(context.getString(R.string.report_partial))
                    add(context.getString(R.string.report_local))
                }
                val file = withContext(Dispatchers.IO) {
                    val directory = File(app.cacheDir, "shared-reports").apply { check(isDirectory || mkdirs()) }
                    directory.listFiles().orEmpty().filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }.forEach { it.delete() }
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 34f; color = Color.rgb(35, 55, 45) }
                    val wrapped = lines.flatMap { line ->
                        buildList {
                            var start = 0
                            while (start < line.length) {
                                val count = paint.breakText(line, start, line.length, true, 880f, null)
                                check(count > 0)
                                add(line.substring(start, start + count)); start += count
                            }
                            add("")
                        }
                    }
                    val bitmap = Bitmap.createBitmap(1000, 120 + wrapped.size * 48, Bitmap.Config.ARGB_8888)
                    try {
                        Canvas(bitmap).apply {
                            drawColor(Color.rgb(248, 247, 240))
                            wrapped.forEachIndexed { index, line -> drawText(line, 60f, 80f + index * 48, paint) }
                        }
                        File(directory, "report-${UUID.randomUUID()}.png").also { file ->
                            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                        }
                    } finally { bitmap.recycle() }
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.reports", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri("report", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, context.getString(R.string.report_share)))
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { status = R.string.export_failed }
            finally { busy = false }
        } }, enabled = !busy) { Text(stringResource(R.string.export_card), style = ComposeTypography.labelLarge) }
        status?.let { Text(stringResource(it), style = ComposeTypography.bodySmall) }
    }
}
