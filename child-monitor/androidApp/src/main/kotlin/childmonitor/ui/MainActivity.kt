package childmonitor.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import childmonitor.android.R
import childmonitor.data.repository.MonitorRepository
import childmonitor.model.SaveState
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.*
import androidx.navigation3.ui.NavDisplay
import childmonitor.android.BuildConfig
import childmonitor.app.MonitorApplication
import childmonitor.model.RunState
import kotlinx.serialization.Serializable
import kotlinx.coroutines.launch

@Serializable data object HomeKey : NavKey
@Serializable data object CapabilityProbeKey : NavKey
@Serializable data object StatisticsKey : NavKey
@Serializable data object StorageKey : NavKey
@Serializable data object SettingsKey : NavKey
@Serializable data class AvatarKey(val fromSettings: Boolean) : NavKey
@Serializable data class AlbumKey(val cleanup: Boolean = false, val sourceResult: String? = null, val protectedId: String? = null) : NavKey
@Serializable data class ResultKey(val sessionId: String, val celebrate: Boolean = false) : NavKey
@Serializable data class PlaybackKey(val sessionId: String) : NavKey

/** 唯一页面宿主，NavEntry 持有状态；失效记录从所有返回路径移除。 */
class MainActivity : ComponentActivity() {
    override fun onStop() {
        (application as MonitorApplication).parentAccess.lock()
        super.onStop()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as MonitorApplication
        setContent { MonitorTheme { MonitorNavigation(app) } }
    }
}

@Composable
private fun MonitorNavigation(app: MonitorApplication) {
    val backStack = rememberNavBackStack(HomeKey)
    val snapshot by app.runtime.snapshotFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val exportState by app.videoExporter.stateFlow.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var pendingExportPath by rememberSaveable { mutableStateOf<String?>(null) }
    // 选择器注册在授权页面之外；授权撤销不丢失用户已发起的保存结果。
    val document = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { uri ->
        val path = pendingExportPath
        pendingExportPath = null
        if (uri != null && path != null) app.videoExporter.export(path, uri)
    }
    LaunchedEffect(exportState.message) {
        exportState.message?.let { snackbar.showSnackbar(app.getString(it)) }
    }
    fun export(result: MonitorRepository.Result) {
        if (pendingExportPath != null || exportState.busy || !app.parentAccess.isAuthorized()) return
        val media = result.media ?: return
        if (media.saveState != SaveState.Saved.name) return
        pendingExportPath = media.relativePath
        try { document.launch("monitor-${result.session.localDate}-${result.session.id.take(8)}.mp4") }
        catch (_: Exception) {
            pendingExportPath = null
            scope.launch { snackbar.showSnackbar(app.getString(R.string.export_failed)) }
        }
    }
    fun go(key: NavKey) {
        if (!snapshot.captureReleased || snapshot.runState !in listOf(RunState.Idle, RunState.Stopped)) return
        val existing = backStack.indexOfFirst { existing ->
            if (existing is ResultKey && key is ResultKey) existing.sessionId == key.sessionId else existing == key
        }
        if (existing >= 0) while (backStack.lastIndex > existing) backStack.removeAt(backStack.lastIndex)
        else backStack.add(key)
    }
    fun back() { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
    fun deleted(ids: Set<String>) {
        if (ids.isEmpty()) return
        val currentDeleted = when (val top = backStack.last()) {
            is PlaybackKey -> top.sessionId in ids
            is ResultKey -> top.sessionId in ids
            else -> false
        }
        backStack.removeAll { key -> when (key) {
            is PlaybackKey -> key.sessionId in ids
            is ResultKey -> key.sessionId in ids
            is AlbumKey -> key.sourceResult in ids
            else -> false
        } }
        if (currentDeleted) { backStack.clear(); backStack.add(HomeKey); backStack.add(AlbumKey()) }
    }
    LaunchedEffect(snapshot.runState, snapshot.ready) {
        if (snapshot.ready && snapshot.runState !in listOf(RunState.Idle, RunState.Stopped) && backStack.lastOrNull() != HomeKey) {
            backStack.clear(); backStack.add(HomeKey)
        }
    }
    Box(Modifier.fillMaxSize()) {
        ParentProtected(app, protected = backStack.lastOrNull() != HomeKey, onCancel = { go(HomeKey) }) {
            NavDisplay(backStack = backStack, onBack = { back() }, entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
                entryProvider = entryProvider {
                    entry<HomeKey> {
                        val model = viewModel { MonitorViewModel(app.runtime) }
                        MonitorRoute(model, app, onOpenProbe = { if (BuildConfig.DEBUG) go(CapabilityProbeKey) },
                            onAlbum = { go(AlbumKey()) }, onSettings = { go(SettingsKey) }, onAvatar = { go(AvatarKey(false)) }, onResult = { id, celebrate -> go(ResultKey(id, celebrate)) }, onCleanup = { go(AlbumKey(cleanup = true)) })
                    }
                    entry<SettingsKey> {
                        val model = viewModel { MonitorViewModel(app.runtime) }
                        SettingsScreen(model, { go(AvatarKey(true)) }, { back() }, { go(StorageKey) })
                    }
                    entry<StatisticsKey> {
                        val model = viewModel { RecordViewModel(app.runtime) }
                        StatisticsScreen(model, { back() })
                    }
                    entry<StorageKey> {
                        val model = viewModel { RecordViewModel(app.runtime) }
                        StorageScreen(model, { go(AlbumKey(cleanup = true)) }, { back() })
                    }
                    entry<AvatarKey> {
                        val model = viewModel { MonitorViewModel(app.runtime) }
                        AvatarScreen(model, { back() })
                    }
                    entry<AlbumKey> { key ->
                        val model = viewModel { RecordViewModel(app.runtime) }
                        AlbumScreen(model, app, key.cleanup, key.protectedId,
                            onOpen = { id, saved -> go(if (saved) PlaybackKey(id) else ResultKey(id)) },
                            onDone = { back(); model.runtime.let { runtime -> scope.launch { runtime.refreshStorage(java.util.UUID.randomUUID().toString()) } } },
                            onStatistics = { go(StatisticsKey) }, onDeleted = ::deleted)
                    }
                    entry<ResultKey> { key ->
                        val model = viewModel { RecordViewModel(app.runtime) }
                        ResultScreen(key.sessionId, model, onDone = { go(HomeKey) }, onPlayback = { go(PlaybackKey(key.sessionId)) },
                            onCleanup = { go(AlbumKey(true, key.sessionId, key.sessionId)) }, onDeleted = ::deleted, exporting = exportState.busy || pendingExportPath != null, onExport = ::export, celebrate = key.celebrate)
                    }
                    entry<PlaybackKey> { key ->
                        val model = viewModel { RecordViewModel(app.runtime) }
                        PlaybackScreen(key.sessionId, model, app, { back() }, { go(ResultKey(key.sessionId)) }, ::deleted)
                    }
                    entry<CapabilityProbeKey> {
                        if (BuildConfig.DEBUG) {
                            val model = viewModel { ProbeViewModel(app) }
                            ProbeRoute(model, { back() })
                        } else LaunchedEffect(Unit) { backStack.clear(); backStack.add(HomeKey) }
                    }
                })
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).safeDrawingPadding())
    }
}
