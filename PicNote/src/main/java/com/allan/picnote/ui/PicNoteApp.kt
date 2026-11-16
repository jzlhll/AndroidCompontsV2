package com.allan.picnote.ui

import android.content.ClipData
import android.content.Intent
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.allan.picnote.PicNoteApplication
import com.allan.picnote.R
import com.allan.picnote.logEx
import com.allan.picnote.ui.theme.PicNoteTheme
import com.au.module_android.Globals
import com.au.module_android.utilsmedia.getUriFromFile
import com.au.module_androiduiex.picker.rememberComposePhotoPicker
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute : NavKey

@Serializable
data object SettingsRoute : NavKey

@Serializable
data object AppearanceRoute : NavKey

@Serializable
data object LanguageRoute : NavKey

@Serializable
data class EditRoute(
    val documentId: String? = null,
    val nodeId: String? = null,
    val anchorId: String? = null,
    val kind: String = "body",
) : NavKey

private val PageSlideAnimationSpec = tween<IntOffset>(durationMillis = 220, easing = FastOutSlowInEasing)

@Composable
fun PicNoteApp(app: PicNoteApplication) {
    val home: PicNoteViewModel = viewModel(factory = PicNoteViewModel.Factory(app))
    val state by home.stateFlow.collectAsStateWithLifecycle()
    LaunchedEffect(app, home) { app.foregroundEntries.collect { home.checkOnForeground(it) } }
    val theme = state.theme ?: return
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val backStack = rememberNavBackStack(HomeRoute)
    val homeActive = backStack.lastOrNull() == HomeRoute
    val openEditor: (EditRoute) -> Unit = { route ->
        if (backStack.lastOrNull() == HomeRoute && !home.stateFlow.value.loading && !home.stateFlow.value.busy) backStack.add(route)
    }
    val snackbar = remember { SnackbarHostState() }
    var pickerDocumentId by rememberSaveable { mutableStateOf<String?>(null) }
    var actionAfterCreate by rememberSaveable { mutableStateOf<String?>(null) }
    var candidatesAfterCreate by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val navigateBack: () -> Unit = {
        if (backStack.size > 1) {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
            actionAfterCreate = null
            candidatesAfterCreate = emptyList()
            backStack.removeLastOrNull()
        }
    }
    val picker = rememberComposePhotoPicker { uris ->
        val documentId = pickerDocumentId
        pickerDocumentId = null
        if (documentId != null && uris.isNotEmpty()) home.importImages(documentId, uris.map { it.toString() })
    }
    val openPicker: (String) -> Unit = { id ->
        if (pickerDocumentId == null && backStack.lastOrNull() == HomeRoute) {
            pickerDocumentId = id
            try { picker.launch() }
            catch (e: CancellationException) { pickerDocumentId = null; throw e }
            catch (e: Exception) {
                pickerDocumentId = null
                logEx(throwable = e, message = "Launch home image picker failed")
                home.showMessage(Globals.getString(R.string.error_open_photo_picker))
            }
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            home.clearMessage()
        }
    }
    LaunchedEffect(state.exportedFile) {
        state.exportedFile?.let { file ->
            home.consumedExport()
            try {
                val uri = checkNotNull(getUriFromFile(context, file)) { Globals.getString(R.string.error_create_share_uri) }
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = if (file.extension.equals("html", ignoreCase = true)) "text/html" else "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri(Globals.getString(R.string.app_name), uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, Globals.getString(R.string.export_document)))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                logEx(throwable = e, message = "Open export share failed")
                home.showMessage(Globals.getString(R.string.error_open_share_sheet))
            }
        }
    }
    PicNoteTheme(theme) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            NavDisplay(
                backStack = backStack,
                onBack = navigateBack,
                transitionSpec = {
                    slideInHorizontally(PageSlideAnimationSpec, initialOffsetX = { it }) togetherWith
                        slideOutHorizontally(PageSlideAnimationSpec, targetOffsetX = { -it })
                },
                popTransitionSpec = {
                    slideInHorizontally(PageSlideAnimationSpec, initialOffsetX = { -it }) togetherWith
                        slideOutHorizontally(PageSlideAnimationSpec, targetOffsetX = { it })
                },
                predictivePopTransitionSpec = { _ ->
                    slideInHorizontally(PageSlideAnimationSpec, initialOffsetX = { -it }) togetherWith
                        slideOutHorizontally(PageSlideAnimationSpec, targetOffsetX = { it })
                },
                entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
                entryProvider = entryProvider {
                    entry<HomeRoute> {
                        Box(Modifier.fillMaxSize()
                            .focusProperties { canFocus = homeActive }
                            .then(if (!homeActive) Modifier.clearAndSetSemantics { } else Modifier)) {
                        HomeScreen(
                            state = state,
                            viewModel = home,
                            onCreate = { openEditor(EditRoute()) },
                            onEdit = openEditor,
                            onPickImages = {
                                if (backStack.lastOrNull() == HomeRoute && !home.stateFlow.value.loading && !home.stateFlow.value.busy) {
                                    val id = home.stateFlow.value.content?.document?.id
                                    if (id == null) { actionAfterCreate = "picker"; openEditor(EditRoute()) }
                                    else openPicker(id)
                                }
                            },
                            onCandidates = { identities ->
                                if (backStack.lastOrNull() == HomeRoute && !home.stateFlow.value.loading && !home.stateFlow.value.busy) {
                                    val id = home.stateFlow.value.content?.document?.id
                                    if (id == null) {
                                        actionAfterCreate = "candidates"
                                        candidatesAfterCreate = identities
                                        openEditor(EditRoute())
                                    } else home.importCandidates(id, identities)
                                }
                            },
                            onAppearance = {
                                if (backStack.lastOrNull() == HomeRoute) backStack.add(AppearanceRoute)
                            },
                            onSettings = {
                                if (backStack.lastOrNull() == HomeRoute) backStack.add(SettingsRoute)
                            },
                            active = homeActive,
                        )
                        }
                    }
                    entry<SettingsRoute> {
                        SettingsScreen(
                            onBack = { if (backStack.lastOrNull() == SettingsRoute) navigateBack() },
                            onLanguage = { if (backStack.lastOrNull() == SettingsRoute) backStack.add(LanguageRoute) },
                        )
                    }
                    entry<AppearanceRoute> {
                        AppearanceScreen(
                            selected = theme,
                            onSelect = home::setTheme,
                            onBack = { if (backStack.lastOrNull() == AppearanceRoute) navigateBack() },
                            saving = state.themeSaving,
                        )
                    }
                    entry<LanguageRoute> {
                        LanguageScreen(onBack = { if (backStack.lastOrNull() == LanguageRoute) navigateBack() })
                    }
                    entry<EditRoute> { route ->
                        val editor: EditorViewModel = viewModel(factory = EditorViewModel.Factory(home.repository, route))
                        EditorScreen(
                            viewModel = editor,
                            repository = home.repository,
                            onBack = {
                                if (backStack.lastOrNull() == route) navigateBack()
                            },
                            onSaved = { id, mirrorFailed ->
                                if (backStack.lastOrNull() == route) {
                                    focusManager.clearFocus(force = true)
                                    keyboard?.hide()
                                    home.mirrorResult(route.documentId ?: id, mirrorFailed)
                                    home.reloadDocument(route.documentId ?: id)
                                    backStack.removeLastOrNull()
                                    if (route.documentId == null) {
                                        when (actionAfterCreate) {
                                            "picker" -> openPicker(id)
                                            "candidates" -> home.importCandidates(id, candidatesAfterCreate)
                                        }
                                    }
                                    actionAfterCreate = null
                                    candidatesAfterCreate = emptyList()
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
