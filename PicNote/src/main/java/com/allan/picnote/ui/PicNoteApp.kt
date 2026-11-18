package com.allan.picnote.ui

import android.content.ClipData
import android.content.Intent
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.allan.picnote.PicNoteApplication
import com.allan.picnote.R
import com.allan.picnote.bluetooth.BluetoothTransferPhase
import com.allan.picnote.bluetooth.BluetoothTransferViewModel
import com.allan.picnote.logEx
import com.allan.picnote.data.ImportMode
import com.allan.picnote.data.repositories.Saved
import com.allan.picnote.media.PhotoAccess
import com.allan.picnote.ui.theme.PicNoteTheme
import com.au.module_android.Globals
import com.au.module_androiduiex.styles.ComposeTheme
import com.au.module_android.utilsmedia.getUriFromFile
import com.au.module_androiduiex.picker.rememberComposePhotoPicker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data object HomeRoute : NavKey

@Serializable
data object SettingsRoute : NavKey

@Serializable
data object AdvancedSettingsRoute : NavKey

@Serializable
data object AppearanceRoute : NavKey

@Serializable
data object LanguageRoute : NavKey

@Serializable
data object ImportModeRoute : NavKey

@Serializable
data class BluetoothSendRoute(val documentId: String) : NavKey

@Serializable
data object BluetoothReceiveRoute : NavKey

@Serializable
enum class AfterDocumentCreate { PickImages, ImportCandidates }

@Serializable
data class EditRoute(
    val documentId: String? = null,
    val nodeId: String? = null,
    val anchorId: String? = null,
    val kind: String = "body",
    val afterCreate: AfterDocumentCreate? = null,
    val entryId: String = UUID.randomUUID().toString(),
) : NavKey

private val PageSlideAnimationSpec = tween<IntOffset>(durationMillis = 220, easing = FastOutSlowInEasing)

private fun pagePopTransition() =
    slideInHorizontally(PageSlideAnimationSpec, initialOffsetX = { -it }) togetherWith
        slideOutHorizontally(PageSlideAnimationSpec, targetOffsetX = { it })

@Composable
fun PicNoteApp(app: PicNoteApplication) {
    val home: PicNoteViewModel = viewModel(factory = PicNoteViewModel.Factory(app))
    LaunchedEffect(app, home) { app.foregroundEntries.collect { home.checkOnForeground(it) } }
    val theme = home.collectHomeState { it.theme } ?: ComposeTheme.Sage
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val backStack = rememberNavBackStack(HomeRoute)
    val homeActive = backStack.lastOrNull() == HomeRoute
    fun canLeaveHome(requireReady: Boolean = true): Boolean {
        val current = home.stateFlow.value
        return backStack.lastOrNull() == HomeRoute && (!requireReady ||
            !current.loading && !current.busy && current.candidateImportConfirmation == null)
    }
    fun navigateFromHome(route: NavKey, requireReady: Boolean = true) {
        if (canLeaveHome(requireReady)) backStack.add(route)
    }
    val snackbar = remember { SnackbarHostState() }
    var pickerDocumentId by rememberSaveable { mutableStateOf<String?>(null) }
    var pickerAfterCreateDocumentId by rememberSaveable { mutableStateOf<String?>(null) }
    val navigateBack: () -> Unit = {
        if (backStack.size > 1) {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
            val removed = backStack.removeLastOrNull()
            if (removed is EditRoute && removed.afterCreate == AfterDocumentCreate.ImportCandidates) {
                home.cancelPreparedCandidateImport(removed.entryId)
            }
        }
    }
    val picker = rememberComposePhotoPicker { uris ->
        val documentId = pickerDocumentId
        pickerDocumentId = null
        if (documentId != null && uris.isNotEmpty()) home.importImages(documentId, uris.map { it.toString() })
    }
    val openPicker: (String) -> Unit = { id ->
        if (pickerDocumentId == null && canLeaveHome(requireReady = false)) {
            pickerDocumentId = id
            try { picker.launch() }
            catch (e: CancellationException) { pickerDocumentId = null; throw e }
            catch (e: Exception) {
                pickerDocumentId = null
                logEx(throwable = e, message = "Launch home image picker failed")
                home.showMessage(Globals.app.getString(R.string.error_open_photo_picker))
            }
        }
    }
    LifecycleResumeEffect(homeActive, pickerAfterCreateDocumentId, pickerDocumentId) {
        val id = pickerAfterCreateDocumentId
        if (homeActive && id != null && pickerDocumentId == null && canLeaveHome(requireReady = false)) {
            pickerAfterCreateDocumentId = null
            openPicker(id)
        }
        onPauseOrDispose { }
    }
    HomeActivityEffects(home, homeActive, snackbar)
    PicNoteTheme(theme) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            NavDisplay(
                backStack = backStack,
                onBack = { if (backStack.lastOrNull() !is EditRoute) navigateBack() },
                transitionSpec = {
                    slideInHorizontally(PageSlideAnimationSpec, initialOffsetX = { it }) togetherWith
                        slideOutHorizontally(PageSlideAnimationSpec, targetOffsetX = { -it })
                },
                popTransitionSpec = { pagePopTransition() },
                predictivePopTransitionSpec = { _ -> pagePopTransition() },
                entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
                entryProvider = entryProvider {
                    entry<HomeRoute> {
                        val state by home.stateFlow.collectAsStateWithLifecycle()
                        Box(Modifier.fillMaxSize()
                            .focusProperties { canFocus = homeActive }
                            .then(if (!homeActive) Modifier.clearAndSetSemantics { } else Modifier)) {
                        HomeScreen(
                            state = state,
                            viewModel = home,
                            onCreate = { navigateFromHome(EditRoute()) },
                            onEdit = { navigateFromHome(it) },
                            onPickImages = {
                                if (canLeaveHome()) {
                                    val id = home.stateFlow.value.content?.document?.id
                                    if (id == null) navigateFromHome(EditRoute(afterCreate = AfterDocumentCreate.PickImages))
                                    else openPicker(id)
                                }
                            },
                            onCandidates = { id, images ->
                                val request = if (canLeaveHome()) home.prepareCandidateImport(id, images) else null
                                if (request != null) {
                                    if (id == null) {
                                        val route = EditRoute(afterCreate = AfterDocumentCreate.ImportCandidates)
                                        home.deferCandidateImport(route.entryId, request)
                                        navigateFromHome(route)
                                    } else {
                                        home.importCandidates(id, request)
                                    }
                                    true
                                } else {
                                    false
                                }
                            },
                            onImportMode = {
                                if (home.stateFlow.value.access == PhotoAccess.Full) navigateFromHome(ImportModeRoute)
                            },
                            onAppearance = { navigateFromHome(AppearanceRoute, requireReady = false) },
                            onSettings = { navigateFromHome(SettingsRoute, requireReady = false) },
                            onBluetoothShare = { navigateFromHome(BluetoothSendRoute(it)) },
                            onBluetoothReceive = { navigateFromHome(BluetoothReceiveRoute) },
                            active = homeActive,
                        )
                        }
                    }
                    entry<SettingsRoute> {
                        val busy = home.collectHomeState { it.loading || it.busy }
                        SettingsScreen(
                            onBack = { if (backStack.lastOrNull() == SettingsRoute) navigateBack() },
                            onLanguage = { if (backStack.lastOrNull() == SettingsRoute) backStack.add(LanguageRoute) },
                            onAdvanced = { if (backStack.lastOrNull() == SettingsRoute) backStack.add(AdvancedSettingsRoute) },
                            onClearCache = home::clearCache,
                            busy = busy,
                        )
                    }
                    entry<AdvancedSettingsRoute> {
                        val settings = home.collectHomeState { it.codeWrapSettings }
                        AdvancedSettingsScreen(
                            settings = settings,
                            onToggleNodeCardWrap = home::toggleNodeCardCodeWrap,
                            onToggleDocumentWrap = home::toggleDocumentCodeWrap,
                            onBack = { if (backStack.lastOrNull() == AdvancedSettingsRoute) navigateBack() },
                        )
                    }
                    entry<AppearanceRoute> {
                        val saving = home.collectHomeState { it.themeSaving }
                        AppearanceScreen(
                            selected = theme,
                            onSelect = home::setTheme,
                            onBack = { if (backStack.lastOrNull() == AppearanceRoute) navigateBack() },
                            saving = saving,
                        )
                    }
                    entry<LanguageRoute> {
                        LanguageScreen(onBack = { if (backStack.lastOrNull() == LanguageRoute) navigateBack() })
                    }
                    entry<ImportModeRoute> {
                        val mode = home.collectHomeState { it.importMode }
                        val enabled = home.collectHomeState { it.importMode != null && !it.importModeSaving && !it.busy && it.access == PhotoAccess.Full }
                        ImportModeScreen(
                            selected = mode ?: ImportMode.AddOnly,
                            onSelect = home::setImportMode,
                            onBack = { if (backStack.lastOrNull() == ImportModeRoute) navigateBack() },
                            enabled = enabled,
                        )
                    }
                    entry<BluetoothSendRoute> { route ->
                        val title = home.collectHomeState { state -> state.documents.firstOrNull { it.id == route.documentId }?.title }
                        val transfer: BluetoothTransferViewModel = viewModel(factory = BluetoothTransferViewModel.Factory(app, route.documentId))
                        BluetoothTransferScreen(
                            viewModel = transfer,
                            receiving = false,
                            sendTitle = title,
                            active = backStack.lastOrNull() == route,
                            onClose = { _, _ ->
                                if (backStack.lastOrNull() == route) navigateBack()
                            },
                        )
                    }
                    entry<BluetoothReceiveRoute> {
                        val transfer: BluetoothTransferViewModel = viewModel(factory = BluetoothTransferViewModel.Factory(app, null))
                        LaunchedEffect(home, transfer) {
                            val result = transfer.stateFlow.first { it.phase == BluetoothTransferPhase.Imported }
                            result.documentId?.let { home.mirrorResult(it, result.mirrorFailed, notifyOnFailure = false) }
                        }
                        BluetoothTransferScreen(
                            viewModel = transfer,
                            receiving = true,
                            sendTitle = null,
                            active = backStack.lastOrNull() == BluetoothReceiveRoute,
                            onClose = { openDocumentId, importedDocument ->
                                if (backStack.lastOrNull() == BluetoothReceiveRoute) {
                                    importedDocument?.let {
                                        home.mirrorResult(it.value, it.mirrorFailed, notifyOnFailure = false)
                                    }
                                    if (openDocumentId != null) home.reloadDocument(openDocumentId)
                                    else if (importedDocument != null) home.refreshDocumentList()
                                    navigateBack()
                                }
                            },
                        )
                    }
                    entry<EditRoute> { route ->
                        val editor: EditorViewModel = viewModel(factory = EditorViewModel.Factory(home.repository, app.codeLanguagePreferences, route))
                        val wrapCode = home.collectHomeState { it.codeWrapSettings?.document == true }
                        fun finishEditor(result: Saved<String>) {
                            val index = backStack.indexOf(route)
                            if (index < 0) return
                            val current = index == backStack.lastIndex
                            val documentId = route.documentId ?: result.value
                            val action = route.afterCreate
                            if (current) {
                                focusManager.clearFocus(force = true)
                                keyboard?.hide()
                            }
                            backStack.removeAt(index)
                            home.showSkippedImages(result.skippedImages)
                            home.mirrorResult(documentId, result.mirrorFailed)
                            if (current || home.stateFlow.value.content?.document?.id == documentId) {
                                home.reloadDocument(documentId)
                            } else {
                                home.refreshDocumentList()
                            }
                            when (action) {
                                AfterDocumentCreate.PickImages -> if (current) pickerAfterCreateDocumentId = documentId
                                AfterDocumentCreate.ImportCandidates -> {
                                    if (current) home.resumeCandidateImport(documentId, route.entryId)
                                    else home.cancelPreparedCandidateImport(route.entryId)
                                }
                                null -> Unit
                            }
                        }
                        LaunchedEffect(editor) {
                            val result = editor.stateFlow.first { it.saved != null }.saved
                            if (result != null) finishEditor(result)
                        }
                        EditorScreen(
                            viewModel = editor,
                            repository = home.repository,
                            wrapCode = wrapCode,
                            active = backStack.lastOrNull() == route,
                            onBack = {
                                if (backStack.lastOrNull() == route) {
                                    val draft = editor.stateFlow.value
                                    val saved = draft.saved
                                    if (saved != null) finishEditor(saved)
                                    else if (!draft.busy) navigateBack()
                                }
                            },
                        )
                    }
                },
            )
            SnackbarHost(snackbar, Modifier.align(androidx.compose.ui.Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(bottom = 24.dp))
        }
    }
}

@Composable
private fun <T> PicNoteViewModel.collectHomeState(select: (HomeState) -> T): T {
    val flow = remember(this, select) { stateFlow.map { select(it) }.distinctUntilChanged() }
    return flow.collectAsStateWithLifecycle(initialValue = select(stateFlow.value)).value
}

@Composable
private fun HomeActivityEffects(home: PicNoteViewModel, homeActive: Boolean, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mediaDeleteRequest = home.collectHomeState { it.mediaDeleteRequest }
    val exportShareRequest = home.collectHomeState { it.exportShareRequest }
    val mediaDeleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        home.completeMediaDeleteRequest(result.resultCode == Activity.RESULT_OK)
    }
    LifecycleResumeEffect(mediaDeleteRequest, homeActive) {
        if (homeActive) home.consumeMediaDeleteRequest()?.let { sender ->
            try {
                mediaDeleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
            } catch (e: CancellationException) {
                home.failMediaDeleteRequest(e)
                throw e
            } catch (e: Exception) {
                logEx(throwable = e, message = "Launch system media deletion failed")
                home.failMediaDeleteRequest(e)
            }
        }
        onPauseOrDispose { }
    }
    LaunchedEffect(home, lifecycle, snackbar) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            home.messagesFlow.collect { snackbar.showSnackbar(it) }
        }
    }
    LifecycleResumeEffect(exportShareRequest, homeActive) {
        val request = exportShareRequest
        if (homeActive && request?.openRequested == true && home.stateFlow.value.exportShareRequest == request) {
            val file = request.file
            try {
                val uri = checkNotNull(getUriFromFile(context, file)) { "Cannot create export share URI" }
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = if (file.extension.equals("html", ignoreCase = true)) "text/html" else "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri(Globals.app.getString(R.string.app_name), uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, Globals.app.getString(R.string.export_document)))
                home.completeExportShare(file, opened = true)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                logEx(throwable = e, message = "Open export share failed")
                home.completeExportShare(file, opened = false)
            }
        }
        onPauseOrDispose { }
    }
}
