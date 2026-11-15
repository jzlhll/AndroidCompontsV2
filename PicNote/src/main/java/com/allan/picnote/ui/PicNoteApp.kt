package com.allan.picnote.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.LocalActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.allan.picnote.PicNoteApplication
import com.allan.picnote.logEx
import com.au.module_androiduiex.styles.composeBackdropBlurSource
import com.au.module_androiduiex.styles.rememberComposeBackdropBlurState
import com.au.module_androiduiex.styles.ComposeAppTheme
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute : NavKey

@Serializable
data class EditRoute(
    val documentId: String? = null,
    val nodeId: String? = null,
    val anchorId: String? = null,
    val after: Boolean = true,
    val chapter: Boolean = false,
) : NavKey

private val PageSlideAnimationSpec = tween<IntOffset>(durationMillis = 220, easing = FastOutSlowInEasing)

@Composable
fun PicNoteApp(app: PicNoteApplication) {
    val home: PicNoteViewModel = viewModel(factory = PicNoteViewModel.Factory(app))
    val state by home.stateFlow.collectAsStateWithLifecycle()
    LaunchedEffect(app, home) { app.foregroundEntries.collect { home.checkOnForeground(it) } }
    val theme = state.theme ?: return
    val activity = LocalActivity.current
    SideEffect {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !theme.dark
                isAppearanceLightNavigationBars = !theme.dark
            }
        }
    }
    val context = LocalContext.current
    val backStack = rememberNavBackStack(HomeRoute)
    val backdrop = rememberComposeBackdropBlurState()
    val overlayStrategy = remember { EditorOverlaySceneStrategy() }
    val homeActive = backStack.lastOrNull() == HomeRoute
    val openEditor: (EditRoute) -> Unit = { route ->
        if (backStack.lastOrNull() == HomeRoute && !home.stateFlow.value.loading && !home.stateFlow.value.busy) backStack.add(route)
    }
    val snackbar = remember { SnackbarHostState() }
    var pickerDocumentId by rememberSaveable { mutableStateOf<String?>(null) }
    var actionAfterCreate by rememberSaveable { mutableStateOf<String?>(null) }
    var candidatesAfterCreate by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        val documentId = pickerDocumentId
        pickerDocumentId = null
        if (documentId != null && uris.isNotEmpty()) home.importImages(documentId, uris.map { it.toString() })
    }
    val openPicker: (String) -> Unit = { id ->
        if (pickerDocumentId == null && backStack.lastOrNull() == HomeRoute) {
            pickerDocumentId = id
            picker.launch(PickVisualMediaRequest.Builder().setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly).setOrderedSelection(true).build())
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            home.clearMessage()
        }
    }
    LaunchedEffect(state.markdownToCopy) {
        state.markdownToCopy?.let { markdown ->
            try {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Markdown", markdown))
                home.showMessage("已复制 Markdown")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                logEx(throwable = e, message = "Copy Markdown failed")
                home.showMessage("复制失败，请重试或导出文档")
            } finally { home.consumedCopy() }
        }
    }
    LaunchedEffect(state.exportedFile) {
        state.exportedFile?.let { file ->
            home.consumedExport()
            try {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.exports", file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri("PicNote", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, "导出文档"))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                logEx(throwable = e, message = "Open export share failed")
                home.showMessage("无法打开分享面板，请重试")
            }
        }
    }
    ComposeAppTheme(theme) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            NavDisplay(
                backStack = backStack,
                onBack = {
                    if (backStack.size > 1) {
                        actionAfterCreate = null
                        candidatesAfterCreate = emptyList()
                        backStack.removeLastOrNull()
                    }
                },
                sceneStrategies = listOf(overlayStrategy),
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
                        Box(Modifier.fillMaxSize().composeBackdropBlurSource(backdrop)
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
                            active = homeActive,
                        )
                        }
                    }
                    entry<EditRoute>(metadata = { route -> if (route.documentId != null) mapOf(EditorOverlayMetadataKey to true) else emptyMap() }) { route ->
                        val editor: EditorViewModel = viewModel(factory = EditorViewModel.Factory(home.repository, route))
                        EditorScreen(
                            viewModel = editor,
                            repository = home.repository,
                            backdrop = backdrop,
                            onBack = {
                                if (backStack.lastOrNull() == route) {
                                    actionAfterCreate = null
                                    candidatesAfterCreate = emptyList()
                                    backStack.removeLastOrNull()
                                }
                            },
                            onSaved = { id, mirrorFailed ->
                                if (backStack.lastOrNull() == route) {
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
