package childmonitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import childmonitor.android.R
import childmonitor.app.MonitorApplication
import childmonitor.data.database.entity.MediaEntity
import kotlinx.coroutines.CancellationException

@Composable
fun MonitorToolbar(app: MonitorApplication, onAlbum: () -> Unit, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    val parent by app.parentAccess.stateFlow.collectAsStateWithLifecycle()
    val revision by app.runtime.records.revisionFlow.collectAsStateWithLifecycle()
    val authorized = parent.ready && (!parent.enabled || parent.unlocked)
    val latest by produceState<MediaEntity?>(null, authorized, revision) {
        value = null
        if (authorized) {
            try { value = app.runtime.records.latestSavedVideo() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { value = null }
        }
    }
    MonitorToolbarContent(onAlbum, onSettings, modifier) {
        val media = if (authorized) latest else null
        Thumbnail(app, media?.relativePath, media?.sessionId.orEmpty(), media != null, Modifier.fillMaxSize()) {
            Icon(painterResource(R.drawable.ic_monitor_album), null, Modifier.size(28.dp))
        }
    }
}

@Composable
private fun MonitorToolbarContent(onAlbum: () -> Unit, onSettings: () -> Unit, modifier: Modifier = Modifier, albumContent: @Composable () -> Unit) {
    val albumDescription = stringResource(R.string.album_title)
    Box(modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.app_name), Modifier.padding(horizontal = 56.dp), style = ComposeTypography.titleLarge, maxLines = 1)
        Box(Modifier.align(Alignment.CenterStart).size(48.dp).clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.Button, onClick = onAlbum)
            .semantics { contentDescription = albumDescription }, contentAlignment = Alignment.Center) { albumContent() }
        IconButton(onSettings, Modifier.align(Alignment.CenterEnd).size(48.dp)) {
            Icon(painterResource(R.drawable.ic_monitor_settings), stringResource(R.string.settings_title), Modifier.size(28.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MonitorToolbarPreview() {
    AppPreview {
        MonitorToolbarContent({}, {}, Modifier.padding(20.dp)) {
            Icon(painterResource(R.drawable.ic_monitor_album), null, Modifier.size(28.dp))
        }
    }
}
