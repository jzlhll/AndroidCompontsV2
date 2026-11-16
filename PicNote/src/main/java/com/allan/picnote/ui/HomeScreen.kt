package com.allan.picnote.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.allan.picnote.R
import com.allan.picnote.data.WelcomeDocumentId
import com.allan.picnote.data.NodeGuideKind
import com.allan.picnote.logEx
import com.allan.picnote.media.PhotoAccess
import com.allan.picnote.media.photoAccess
import com.allan.picnote.media.photoPermissions
import com.allan.picnote.ui.components.CreateDocumentButton
import com.au.module_android.Globals
import com.au.module_androiduiex.styles.ComposeTheme
import com.au.module_androiduiex.styles.ComposeLineTabLayout
import com.au.module_androiduiex.styles.ComposeLineTabLayoutStyle
import com.au.module_androiduiex.styles.ComposeTypography
import com.au.module_androiduiex.styles.ComposeButton as Button
import com.au.module_androiduiex.styles.ComposeTextButton as TextButton
import com.au.module_androiduiex.styles.ComposeIconButton as IconButton
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeState,
    viewModel: PicNoteViewModel,
    onCreate: () -> Unit,
    onEdit: (EditRoute) -> Unit,
    onPickImages: () -> Unit,
    onCandidates: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = true,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val openSettings = {
        try {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        } catch (e: Exception) {
            logEx(throwable = e, message = "Open image permission settings failed")
            viewModel.showMessage(Globals.app.getString(R.string.error_open_settings))
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.updatePhotoAccess()
    }
    LifecycleResumeEffect(state.permissionRequestPending, active) {
        if (active && viewModel.consumePermissionRequest()) {
            when (photoAccess(context)) {
                PhotoAccess.Full -> viewModel.updatePhotoAccess()
                PhotoAccess.Partial -> openSettings()
                PhotoAccess.Denied -> try {
                    permissionLauncher.launch(photoPermissions())
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    logEx(throwable = e, message = "Launch image permission request failed")
                    viewModel.showMessage(Globals.app.getString(R.string.error_request_photo_permission))
                }
            }
        }
        onPauseOrDispose { }
    }
    LaunchedEffect(state.permissionHistory, state.permissionHistoryLoading, state.permissionHistoryFailed, state.nodeGuide, active) {
        val history = state.permissionHistory
        if (active && state.nodeGuide == null && !state.permissionHistoryLoading && !state.permissionHistoryFailed && history != null && !history.explanationSeen && !history.requested
            && photoAccess(context) != PhotoAccess.Full) viewModel.showPermissionPrompt()
    }
    val requestPermission = {
        val access = photoAccess(context)
        val history = viewModel.stateFlow.value.permissionHistory
        when {
            access == PhotoAccess.Full -> viewModel.updatePhotoAccess()
            access == PhotoAccess.Partial || history == null -> openSettings()
            !history.requested || (activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, photoPermissions().first())) -> viewModel.showPermissionPrompt()
            else -> openSettings()
        }
    }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val documentStates = rememberSaveableStateHolder()
    val scope = rememberCoroutineScope()
    val ready = active && !state.busy && !state.loading
    var more by remember { mutableStateOf(false) }
    var renameId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameTitle by rememberSaveable { mutableStateOf("") }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var ribbon by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var appearanceDialog by rememberSaveable { mutableStateOf(false) }
    var settingsDialog by rememberSaveable { mutableStateOf(false) }
    val content = state.content
    val document = content?.document
    val guide = state.nodeGuide
    val guideAnchor = remember(guide) { guide?.let { NodeGuideAnchor() } }
    val guideNodeId = if (guide?.kind == NodeGuideKind.Menu) content?.nodes?.getOrNull(2)?.node?.id else null
    val showMediaActions = document?.id != WelcomeDocumentId
    val latestPending = state.pending.firstOrNull()
    val showLoading = state.loading || state.busy || state.scanning
    val imported = remember(state.content) { state.content?.nodes.orEmpty().flatMap { it.images }.mapNotNull { it.sourceUri }.toSet() }
    val locale = context.resources.configuration.locales[0]
    val dateFormatter = remember(locale) { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale) }
    val openDocumentListDescription = stringResource(R.string.open_document_list)
    val documentActionsDescription = stringResource(R.string.document_actions)
    val openPickerDescription = stringResource(R.string.open_picker_add_images)
    LaunchedEffect(state.pending, imported) {
        selected = selected.filter { id -> state.pending.any { it.uri == id } && id !in imported }
        if (state.pending.isEmpty()) ribbon = false
    }
    val beginRename: (String, String) -> Unit = { id, title -> renameId = id; renameTitle = title }

    ModalNavigationDrawer(
        modifier = modifier,
        drawerState = drawer,
        gesturesEnabled = ready,
        drawerContent = {
            ModalDrawerSheet(Modifier.fillMaxWidth(0.86f)) {
                Column(Modifier.fillMaxHeight()) {
                Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.my_documents), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    CreateDocumentButton(stringResource(R.string.new_document_button), onClick = { scope.launch { drawer.close(); onCreate() } }, enabled = ready)
                }
                LazyColumn(Modifier.weight(1f).selectableGroup()) {
                    items(state.documents, key = { it.id }, contentType = { "document" }) { item ->
                        var menu by remember { mutableStateOf(false) }
                        val updatedAt = remember(item.updatedAt, dateFormatter) { dateFormatter.format(Date(item.updatedAt)) }
                        val itemActionsDescription = stringResource(R.string.document_item_actions, item.title)
                        Row(
                            Modifier.fillMaxWidth().background(if (item.id == document?.id) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .semantics { this.selected = item.id == document?.id }
                                .clickable(enabled = ready) { viewModel.reloadDocument(item.id); scope.launch { drawer.close() } }.padding(start = 20.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(stringResource(R.string.document_summary, item.nodeCount, updatedAt),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Box {
                                IconButton(onClick = { menu = true }, enabled = ready,
                                    modifier = Modifier.semantics { contentDescription = itemActionsDescription }) { Text("⋯") }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { menu = false; beginRename(item.id, item.title) })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.delete_document), color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; deleteId = item.id })
                                }
                            }
                        }
                    }
                }
                HorizontalDivider()
                TextButton(onClick = { appearanceDialog = true }, enabled = active && !state.themeSaving,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) { Text(stringResource(R.string.appearance)) }
                TextButton(onClick = { scope.launch { drawer.close(); settingsDialog = true } }, enabled = active,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) { Text(stringResource(R.string.settings)) }
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    expandedHeight = 64.dp,
                    title = { Text(document?.title ?: stringResource(R.string.app_name), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawer.open() } }, enabled = active,
                            modifier = Modifier.semantics { contentDescription = openDocumentListDescription }) { Text("☰") }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { more = true }, enabled = document != null && ready,
                                modifier = Modifier.semantics { contentDescription = documentActionsDescription }) { Text("⋯") }
                            DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                                if (document != null) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.rename), style = ComposeTypography.Font16, color = LocalContentColor.current) },
                                        onClick = { more = false; beginRename(document.id, document.title) }, enabled = ready)
                                    DropdownMenuItem(text = { Text(stringResource(R.string.export_document), style = ComposeTypography.Font16, color = LocalContentColor.current) },
                                        onClick = { more = false; viewModel.export() }, enabled = ready)
                                    DropdownMenuItem(text = { Text(stringResource(R.string.delete_document), style = ComposeTypography.Font16, color = MaterialTheme.colorScheme.error) },
                                        onClick = { more = false; deleteId = document.id }, enabled = ready)
                                }
                            }
                        }
                    },
                )
            },
            floatingActionButtonPosition = FabPosition.Center,
            floatingActionButton = {
                if (showMediaActions) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            latestPending?.let { latest ->
                                Surface(
                                    onClick = { ribbon = !ribbon }, enabled = ready,
                                    modifier = Modifier.height(56.dp), shape = RoundedCornerShape(16.dp),
                                    color = if (ribbon) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                ) {
                                    Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        LocalImage(latest.uri, stringResource(R.string.latest_image, latest.name), Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)), crop = true)
                                        Text(stringResource(R.string.new_images_count, state.pending.size), Modifier.weight(1f, fill = false).padding(end = 8.dp),
                                            style = ComposeTypography.Font14M, color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                        FloatingActionButton(
                            onClick = { if (ready) onPickImages() },
                            modifier = Modifier.semantics { contentDescription = openPickerDescription; if (!ready) disabled() },
                            containerColor = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (ready) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            },
            bottomBar = {
                if (showMediaActions && ribbon && state.pending.isNotEmpty()) {
                    Surface(tonalElevation = 2.dp) {
                        Column(Modifier.navigationBarsPadding()) {
                            LazyRow(Modifier.fillMaxWidth().padding(top = 12.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(state.pending, key = { it.uri }, contentType = { "candidate" }) { candidate ->
                                    val disabled = candidate.uri in imported || !ready
                                    Column(Modifier.width(104.dp).clickable(enabled = !disabled) {
                                        selected = if (candidate.uri in selected) selected - candidate.uri else selected + candidate.uri
                                    }) {
                                        LocalImage(candidate.uri, stringResource(R.string.image_named, candidate.name), Modifier.size(104.dp), crop = true)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(checked = candidate.uri in selected, onCheckedChange = null, enabled = !disabled)
                                            if (candidate.uri in imported) Text(stringResource(R.string.already_added), style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { viewModel.ignoreCandidates(selected); selected = emptyList() }, enabled = selected.isNotEmpty() && ready) { Text(stringResource(R.string.ignore)) }
                                Button(onClick = { onCandidates(selected); selected = emptyList() }, enabled = selected.isNotEmpty() && ready) { Text(stringResource(R.string.add_images_count, selected.size)) }
                            }
                        }
                    }
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                documentStates.SaveableStateProvider(document?.id ?: "empty") {
                    val pager = rememberPagerState(pageCount = { 2 })
                    val nodeScroll = rememberLazyListState()
                    val documentScroll = rememberLazyListState()
                    LaunchedEffect(guide, active) {
                        if (active && guide != null && content != null && guide.documentId == content.document.id) {
                            drawer.close()
                            pager.scrollToPage(0)
                            if (guide.kind == NodeGuideKind.Menu) {
                                nodeScroll.scrollToItem(2)
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        ComposeLineTabLayout(
                            tabs = listOf(stringResource(R.string.tab_nodes), stringResource(R.string.tab_document)), selectedIndex = pager.currentPage,
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
                            style = ComposeLineTabLayoutStyle(
                                height = 48.dp, startPadding = 0.dp, itemHorizontalPadding = 0.dp,
                                selectedTextStyle = ComposeTypography.Font16M, unselectedTextStyle = ComposeTypography.Font16,
                                distributeEvenly = true, indicatorWidth = 51.dp,
                            ),
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineColor = MaterialTheme.colorScheme.outlineVariant,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            onTabClick = { index -> if (content != null) scope.launch { pager.animateScrollToPage(index) } },
                        )
                        if (showLoading) CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center).size(24.dp), strokeWidth = 2.dp,
                        )
                    }
                    PermissionBanner(state, onPermission = requestPermission, active = active)
                    if (state.mirrorFailed) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.mirror_failed), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = viewModel::retryMirror, enabled = ready) { Text(stringResource(R.string.retry)) }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (content != null) {
                            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), userScrollEnabled = false, beyondViewportPageCount = 1, key = { it }) { page ->
                                if (page == 0) NodeList(
                                    content = content, repository = viewModel.repository, listState = nodeScroll, enabled = ready, active = active && pager.currentPage == 0,
                                    onEdit = { node -> onEdit(EditRoute(content.document.id, node.node.id, kind = node.node.kind)) },
                                    onInsert = { anchor, kind -> onEdit(EditRoute(content.document.id, anchorId = anchor, kind = kind)) },
                                    onMove = viewModel::move, onDelete = viewModel::deleteNode, onMerge = viewModel::mergeNodes,
                                    onMoveImage = viewModel::moveImage,
                                    onNodeMenuOpened = { viewModel.recordNodeClick(NodeGuideKind.Menu) },
                                    onNodeCardClicked = { viewModel.recordNodeClick(NodeGuideKind.Card) },
                                    bottomContentPadding = if (showMediaActions) 96.dp else 24.dp,
                                    guideNodeId = guideNodeId, guideKind = guide?.kind,
                                    guideAnchor = if (guide != null && pager.currentPage == 0) guideAnchor else null,
                                ) else DocumentPreview(content, viewModel.repository, documentScroll, onMessage = viewModel::showMessage,
                                    active = active && pager.currentPage == 1, bottomContentPadding = if (showMediaActions) 96.dp else 24.dp)
                            }
                        }
                    }
                }
            }
        }
    }
    if (active && guide != null && guideAnchor != null && guide.documentId == document?.id) NodeGuideOverlay(
        anchor = guideAnchor, kind = guide.kind, saving = state.nodeGuideSaving, onAcknowledge = viewModel::acknowledgeNodeGuide,
    )
    if (active && guide == null && state.permissionPromptVisible) AlertDialog(
        onDismissRequest = viewModel::dismissPermissionPrompt,
        title = { Text(stringResource(R.string.grant_photo_access_title)) },
        text = { Text(stringResource(R.string.grant_photo_access_message)) },
        confirmButton = { TextButton(onClick = viewModel::requestImagePermission, enabled = !state.permissionPromptSaving) { Text(stringResource(R.string.grant_permission)) } },
        dismissButton = { TextButton(onClick = viewModel::dismissPermissionPrompt, enabled = !state.permissionPromptSaving) { Text(stringResource(R.string.not_now)) } },
    )
    if (active && renameId != null) AlertDialog(
        onDismissRequest = { renameId = null }, title = { Text(stringResource(R.string.rename_document)) },
        text = { OutlinedTextField(value = renameTitle, onValueChange = { renameTitle = it }, singleLine = true, label = { Text(stringResource(R.string.title)) }) },
        confirmButton = { TextButton(onClick = { viewModel.rename(renameId!!, renameTitle); renameId = null }, enabled = renameTitle.trim().isNotEmpty() && ready) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = { renameId = null }) { Text(stringResource(R.string.cancel)) } },
    )
    if (active && deleteId != null) AlertDialog(
        onDismissRequest = { deleteId = null }, title = { Text(stringResource(R.string.delete_document), color = MaterialTheme.colorScheme.error) },
        text = { Text(stringResource(R.string.delete_document_message, state.documents.firstOrNull { it.id == deleteId }?.title.orEmpty())) },
        confirmButton = { TextButton(onClick = { viewModel.deleteDocument(deleteId!!); deleteId = null }, enabled = ready) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deleteId = null }) { Text(stringResource(R.string.cancel)) } },
    )
    if (active && appearanceDialog) AlertDialog(
        onDismissRequest = { appearanceDialog = false }, title = { Text(stringResource(R.string.appearance)) },
        text = {
            Column(Modifier.selectableGroup()) {
                ComposeTheme.entries.forEach { theme ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = state.theme == theme, enabled = !state.themeSaving, role = Role.RadioButton,
                        onClick = { viewModel.setTheme(theme); appearanceDialog = false }).padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = state.theme == theme, onClick = null, enabled = !state.themeSaving)
                        Text(theme.name, style = ComposeTypography.Font16, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { appearanceDialog = false }) { Text(stringResource(R.string.close)) } },
    )
    if (active && settingsDialog) AlertDialog(
        onDismissRequest = { settingsDialog = false }, title = { Text(stringResource(R.string.settings)) },
        text = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) {
                        settingsDialog = false
                        try {
                            context.startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:${context.packageName}")))
                        } catch (e: Exception) {
                            logEx(throwable = e, message = "Open language settings failed")
                            viewModel.showMessage(Globals.app.getString(R.string.error_open_language_settings))
                        }
                    }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.language), style = ComposeTypography.Font16, color = MaterialTheme.colorScheme.onSurface)
                }
            } else {
                Box(Modifier.fillMaxWidth().heightIn(min = 160.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.settings_empty), style = MaterialTheme.typography.headlineSmall, fontSize = 26.sp, textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = { settingsDialog = false }) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun PermissionBanner(state: HomeState, onPermission: () -> Unit, active: Boolean) {
    if (state.access == PhotoAccess.Full && state.scanError == null) return
    val managePhotoAccessDescription = stringResource(R.string.manage_photo_access)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        if (state.access != PhotoAccess.Full) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(if (state.access == PhotoAccess.Partial) R.string.photo_access_partial else R.string.photo_access_denied),
                Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            IconButton(onClick = onPermission,
                enabled = active && !state.permissionHistoryLoading && !state.permissionPromptSaving && !state.permissionRequestPending,
                modifier = Modifier.semantics { contentDescription = managePhotoAccessDescription }) {
                Text("›", style = MaterialTheme.typography.titleLarge)
            }
        }
        if (state.scanError != null) Text(state.scanError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
