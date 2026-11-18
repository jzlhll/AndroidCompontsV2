package com.allan.picnote.ui

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.allan.picnote.R
import com.allan.picnote.data.WelcomeDocumentId
import com.allan.picnote.data.NodeGuideKind
import com.allan.picnote.data.ImportMode
import com.allan.picnote.data.entity.PendingMediaEntity
import com.allan.picnote.logEx
import com.allan.picnote.media.PhotoAccess
import com.allan.picnote.media.photoAccess
import com.allan.picnote.media.photoPermissions
import com.allan.picnote.ui.components.BluetoothReceiveButton
import com.allan.picnote.ui.components.CreateDocumentButton
import com.allan.picnote.ui.components.ImportModeIcon
import com.au.module_android.Globals
import com.au.module_androiduiex.dialogs.ComposeBottomSheet
import com.au.module_androiduiex.dialogs.ComposeBottomSheetToolbar
import com.au.module_androiduiex.landscape.LandscapePaneLayout
import com.au.module_androiduiex.styles.ComposeLineTabLayout
import com.au.module_androiduiex.styles.ComposeLineTabLayoutStyle
import com.au.module_androiduiex.styles.ComposeTypography
import com.au.module_androiduiex.styles.ComposeButton as Button
import com.au.module_androiduiex.styles.ComposeOutlinedButton as OutlinedButton
import com.au.module_androiduiex.styles.ComposeTextButton as TextButton
import com.au.module_androiduiex.styles.ComposeIconButton as IconButton
import com.au.module_androiduiex.styles.navBarInsetBaseline
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
    onCandidates: (String?, List<PendingMediaEntity>) -> Boolean,
    onImportMode: () -> Unit,
    onAppearance: () -> Unit,
    onSettings: () -> Unit,
    onBluetoothShare: (String) -> Unit,
    onBluetoothReceive: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = true,
) {
    val context = LocalContext.current
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
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
    var knownDocumentIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(state.loading, state.documents, documentStates) {
        if (!state.loading) {
            val ids = state.documents.map { it.id }.toSet()
            knownDocumentIds.filter { it !in ids }.forEach(documentStates::removeState)
            knownDocumentIds = ids.toList()
        }
    }
    val scope = rememberCoroutineScope()
    val ready = active && !state.busy && !state.loading && state.candidateImportConfirmation == null
    var more by rememberSaveable { mutableStateOf(false) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var ribbon by rememberSaveable { mutableStateOf(false) }
    val content = state.content
    val document = content?.document
    val selected = state.candidateSelection
    val mergeSelection = state.mergeSelection
    var merging by rememberSaveable(document?.id) { mutableStateOf<List<String>?>(null) }
    val bodyNodeIds = remember(content?.nodes) {
        content?.nodes.orEmpty().filter { it.node.kind == "body" }.map { it.node.id }.toSet()
    }
    val selectedNodeIds = remember(mergeSelection) { mergeSelection?.toSet() }
    val currentSelection = mergeSelection
    if (currentSelection != null && currentSelection.any { it !in bodyNodeIds }) {
        SideEffect { viewModel.setMergeSelection(currentSelection.filter { it in bodyNodeIds }) }
    }
    val guide = state.nodeGuide
    val guideAnchor = remember(guide) { guide?.let { NodeGuideAnchor() } }
    val guideNodeId = if (guide?.kind == NodeGuideKind.Menu) content?.nodes?.getOrNull(2)?.node?.id else null
    val showMediaActions = document?.id != WelcomeDocumentId
    val showEditingMediaActions = showMediaActions && mergeSelection == null
    val latestPending = state.pending.firstOrNull()
    val showLoading = state.loading || state.busy || state.scanning || state.codeWrapSettings == null
    val imported = content?.importedSourceUris.orEmpty()
    LaunchedEffect(guide, guideNodeId) {
        if (guide?.kind == NodeGuideKind.Menu && guideNodeId == null) viewModel.dismissNodeGuide()
    }
    val locale = context.resources.configuration.locales[0]
    val dateFormatter = remember(locale) { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale) }
    val openDocumentListDescription = stringResource(R.string.open_document_list)
    val documentActionsDescription = stringResource(R.string.document_actions)
    val openPickerDescription = stringResource(R.string.open_picker_add_images)
    val closeDescription = stringResource(R.string.close)
    val mergeBackState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(
        state = mergeBackState,
        isBackEnabled = active && mergeSelection != null && merging == null && drawer.isClosed,
        onBackCompleted = { viewModel.setMergeSelection(null) },
    )
    LaunchedEffect(state.pending) {
        if (state.pending.isEmpty()) ribbon = false
    }
    LaunchedEffect(active) {
        if (!active) {
            ribbon = false
            viewModel.setMergeSelection(null)
            merging = null
        }
    }
    val documentActions: @Composable (Boolean) -> Unit = { showMerge ->
        Box {
            IconButton(onClick = { more = true }, enabled = document != null && ready && mergeSelection == null,
                modifier = Modifier.semantics { contentDescription = documentActionsDescription }) {
                Icon(Icons.Default.MoreVert, contentDescription = null)
            }
            DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                if (document != null) {
                    if (showMerge) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.merge), style = ComposeTypography.Font16, color = LocalContentColor.current) },
                            onClick = {
                                more = false
                                ribbon = false
                                viewModel.setMergeSelection(emptyList())
                            },
                            enabled = ready && bodyNodeIds.size >= 2,
                        )
                    }
                    BluetoothShareMenuItem(
                        onClick = { more = false; onBluetoothShare(document.id) },
                        enabled = ready,
                    )
                    DropdownMenuItem(text = { Text(stringResource(R.string.export_full), style = ComposeTypography.Font16, color = LocalContentColor.current) },
                        onClick = { more = false; viewModel.exportFull(document.id) }, enabled = ready)
                    DropdownMenuItem(text = { Text(stringResource(R.string.export_web), style = ComposeTypography.Font16, color = LocalContentColor.current) },
                        onClick = { more = false; viewModel.exportWeb(document.id) }, enabled = ready)
                    DropdownMenuItem(text = { Text(stringResource(R.string.duplicate_document), style = ComposeTypography.Font16, color = LocalContentColor.current) },
                        onClick = { more = false; viewModel.duplicateDocument(document.id) }, enabled = ready)
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete_document), style = ComposeTypography.Font16, color = MaterialTheme.colorScheme.error) },
                        onClick = { more = false; deleteId = document.id }, enabled = ready)
                }
            }
        }
    }
    val mediaActions: @Composable (Modifier) -> Unit = { actionsModifier ->
        Row(actionsModifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
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
                            Text("+${state.pending.size}", Modifier.padding(start = 4.dp, end = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = ComposeTypography.Font14M, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            FloatingActionButton(
                onClick = { if (ready) onPickImages() },
                modifier = Modifier.size(if (landscape) 52.dp else 56.dp)
                    .semantics { contentDescription = openPickerDescription; if (!ready) disabled() },
                containerColor = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (ready) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(24.dp))
            }
        }
    }
    val statusMessages: @Composable () -> Unit = {
        PermissionBanner(state, onPermission = requestPermission, active = active)
        if (state.mirrorFailed) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.mirror_failed), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = viewModel::retryMirror, enabled = ready) { Text(stringResource(R.string.retry)) }
        }
        if (state.exportShareRequest?.openRequested == false) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.error_open_share_sheet), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = viewModel::retryExportShare, enabled = ready) { Text(stringResource(R.string.retry)) }
            }
        }
    }

    ModalNavigationDrawer(
        modifier = modifier,
        drawerState = drawer,
        gesturesEnabled = ready && mergeSelection == null,
        drawerContent = {
            ModalDrawerSheet(Modifier.fillMaxWidth(if (landscape) 0.65f else 0.86f)) {
                Column(Modifier.fillMaxHeight()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.my_documents), Modifier.weight(1f),
                                style = MaterialTheme.typography.titleLarge, fontSize = 21.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val newDocumentLabel = stringResource(R.string.new_document_button)
                            BluetoothReceiveButton(
                                newDocumentLabel,
                                stringResource(R.string.bluetooth_receive),
                                onClick = { scope.launch { drawer.snapTo(DrawerValue.Closed); onBluetoothReceive() } },
                                enabled = ready,
                            )
                            Spacer(Modifier.width(4.dp))
                            CreateDocumentButton(newDocumentLabel,
                                onClick = { scope.launch { drawer.snapTo(DrawerValue.Closed); onCreate() } }, enabled = ready)
                        }
                        if (landscape) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { scope.launch { drawer.snapTo(DrawerValue.Closed); onAppearance() } }, enabled = active && !state.themeSaving,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 9.dp)) {
                                    Text(stringResource(R.string.appearance))
                                }
                                TextButton(onClick = { scope.launch { drawer.snapTo(DrawerValue.Closed); onSettings() } }, enabled = active,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 9.dp)) {
                                    Text(stringResource(R.string.settings))
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                    LazyColumn(Modifier.weight(1f).selectableGroup(), contentPadding = PaddingValues(bottom = 12.dp)) {
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
                                        BluetoothShareMenuItem(
                                            onClick = {
                                                menu = false
                                                scope.launch { drawer.snapTo(DrawerValue.Closed); onBluetoothShare(item.id) }
                                            },
                                            enabled = ready,
                                        )
                                        DropdownMenuItem(text = { Text(stringResource(R.string.export_full)) }, enabled = ready,
                                            onClick = { menu = false; viewModel.exportFull(item.id) })
                                        DropdownMenuItem(text = { Text(stringResource(R.string.export_web)) }, enabled = ready,
                                            onClick = { menu = false; viewModel.exportWeb(item.id) })
                                        DropdownMenuItem(text = { Text(stringResource(R.string.duplicate_document)) }, enabled = ready,
                                            onClick = { menu = false; viewModel.duplicateDocument(item.id) })
                                        DropdownMenuItem(text = { Text(stringResource(R.string.delete_document), color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; deleteId = item.id })
                                    }
                                }
                            }
                        }
                    }
                    if (!landscape) {
                        HorizontalDivider()
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).navBarInsetBaseline()) {
                            Row(Modifier.fillMaxWidth().height(48.dp).clickable(enabled = active && !state.themeSaving) {
                                scope.launch { drawer.snapTo(DrawerValue.Closed); onAppearance() }
                            }, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.appearance), color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.fillMaxWidth().height(48.dp).clickable(enabled = active) {
                                scope.launch { drawer.snapTo(DrawerValue.Closed); onSettings() }
                            }, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.settings), color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        },
    ) {
        documentStates.SaveableStateProvider(document?.id ?: "empty") {
            val pager = rememberPagerState(pageCount = { 2 })
            val nodeScroll = rememberLazyListState()
            val documentScroll = rememberLazyListState()
            val tabs = listOf(stringResource(R.string.tab_nodes), stringResource(R.string.tab_document))
            val onTabSelected: (Int) -> Unit = { index ->
                if (active && content != null) {
                    viewModel.setMergeSelection(null)
                    merging = null
                    scope.launch { pager.animateScrollToPage(index) }
                }
            }
            val openDocumentList: () -> Unit = {
                viewModel.setMergeSelection(null)
                merging = null
                scope.launch { drawer.open() }
            }
            val mergeActions: @Composable () -> Unit = {
                val selection = mergeSelection
                if (selection != null) {
                    TextButton(onClick = { viewModel.setMergeSelection(null) }, enabled = ready) {
                        Text(stringResource(R.string.cancel))
                    }
                    TextButton(onClick = { merging = selection }, enabled = ready && selection.size >= 2) {
                        Text(stringResource(R.string.merge_selected_count, selection.size))
                    }
                }
            }
            LaunchedEffect(guide, active) {
                if (active && guide != null && content != null && guide.documentId == content.document.id) {
                    drawer.close()
                    pager.scrollToPage(0)
                    if (guide.kind == NodeGuideKind.Menu) nodeScroll.scrollToItem(2)
                }
            }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val useSidePanel = maxWidth > maxHeight && maxWidth >= 480.dp
                Scaffold(
                    contentWindowInsets = if (useSidePanel) WindowInsets.safeDrawing else ScaffoldDefaults.contentWindowInsets,
                    topBar = {
                        if (!useSidePanel) TopAppBar(
                            expandedHeight = 54.dp,
                            title = { Text(document?.title ?: stringResource(R.string.app_name), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            navigationIcon = {
                                IconButton(onClick = openDocumentList, enabled = active,
                                    modifier = Modifier.semantics { contentDescription = openDocumentListDescription }) {
                                    Icon(Icons.Default.Menu, contentDescription = null)
                                }
                            },
                            actions = {
                                if (mergeSelection == null) documentActions(pager.currentPage == 0)
                                else mergeActions()
                            },
                        )
                    },
                    floatingActionButtonPosition = FabPosition.Center,
                    floatingActionButton = {
                        if (!useSidePanel && showEditingMediaActions) {
                            mediaActions(Modifier.fillMaxWidth().padding(horizontal = 16.dp).navBarInsetBaseline())
                        }
                    },
                ) { padding ->
                    LandscapePaneLayout(
                        showSidePane = useSidePanel,
                        sidePane = {
                            Box(Modifier.fillMaxSize()) {
                                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                                    .padding(start = 12.dp, end = 12.dp, bottom = if (showEditingMediaActions) 80.dp else 12.dp)) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = openDocumentList, enabled = active,
                                            modifier = Modifier.semantics { contentDescription = openDocumentListDescription }) {
                                            Icon(Icons.Default.Menu, contentDescription = null)
                                        }
                                        Text(stringResource(R.string.app_name), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (showLoading) CircularProgressIndicator(Modifier.padding(horizontal = 4.dp).size(18.dp), strokeWidth = 2.dp)
                                        documentActions(pager.currentPage == 0)
                                    }
                                    Text(document?.title ?: stringResource(R.string.app_name),
                                        Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 20.dp),
                                        maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge)
                                    HomeSideTabs(tabs, pager.currentPage, onTabSelected, enabled = active && content != null)
                                    Spacer(Modifier.height(12.dp))
                                    statusMessages()
                                }
                                if (showEditingMediaActions) {
                                    mediaActions(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp).navBarInsetBaseline())
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
                    ) {
                        Column(Modifier.fillMaxSize()) {
                            if (useSidePanel && mergeSelection != null) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                                    mergeActions()
                                }
                            }
                            if (!useSidePanel) {
                                Box(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                                    ComposeLineTabLayout(
                                        tabs = tabs, selectedIndex = pager.currentPage,
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
                                        onTabClick = onTabSelected,
                                    )
                                    if (showLoading) CircularProgressIndicator(
                                        modifier = Modifier.align(Alignment.Center).size(24.dp), strokeWidth = 2.dp,
                                    )
                                }
                                statusMessages()
                            }
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                val wrapSettings = state.codeWrapSettings
                                if (content != null && wrapSettings != null) {
                                    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), userScrollEnabled = false, beyondViewportPageCount = 1, key = { it }) { page ->
                                        if (page == 0) NodeList(
                                            content = content, repository = viewModel.repository, listState = nodeScroll, enabled = ready, active = active && pager.currentPage == 0,
                                            onEdit = { node -> onEdit(EditRoute(content.document.id, node.node.id, kind = node.node.kind)) },
                                            onInsert = { anchor, kind -> onEdit(EditRoute(content.document.id, anchorId = anchor, kind = kind)) },
                                            onMove = viewModel::move, onDelete = viewModel::deleteNode, onMerge = { merging = it },
                                            onSelectNode = { id ->
                                                val selection = mergeSelection
                                                if (ready && selection != null && id in bodyNodeIds) {
                                                    viewModel.setMergeSelection(if (id in selection) selection - id else selection + id)
                                                }
                                            },
                                            onMoveImage = viewModel::moveImage,
                                            onNodeMenuOpened = { viewModel.recordNodeClick(NodeGuideKind.Menu) },
                                            onNodeCardClicked = { viewModel.recordNodeClick(NodeGuideKind.Card) },
                                            wrapCode = wrapSettings.nodeCard,
                                            selectedNodeIds = selectedNodeIds,
                                            bottomContentPadding = if (!useSidePanel && showEditingMediaActions) 96.dp else 24.dp,
                                            guideNodeId = guideNodeId, guideKind = guide?.kind,
                                            guideAnchor = if (guide != null && pager.currentPage == 0) guideAnchor else null,
                                        ) else DocumentPreview(content, viewModel.repository, documentScroll, onMessage = viewModel::showMessage,
                                            onDownloadImage = viewModel::downloadImage, wrapCode = wrapSettings.document,
                                            active = active && pager.currentPage == 1, enabled = ready,
                                            bottomContentPadding = if (!useSidePanel && showEditingMediaActions) 96.dp else 24.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (active && content != null) merging?.let { ids ->
        MergeNodesDialog(
            content = content, nodeIds = ids, enabled = ready,
            onDismiss = { merging = null },
            onConfirm = {
                merging = null
                viewModel.mergeNodes(ids)
            },
        )
    }
    if (active && showEditingMediaActions && ribbon && state.pending.isNotEmpty()) {
        ComposeBottomSheet(
            onDismissRequest = { ribbon = false },
            backgroundColor = MaterialTheme.colorScheme.surfaceContainerLow,
            showToolbar = ComposeBottomSheetToolbar.Custom { dismiss ->
                Row(Modifier.fillMaxWidth().height(48.dp).padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.recent_new_images, selected.size, state.pending.size),
                        Modifier.weight(1f).padding(start = 20.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = ComposeTypography.Font16M, color = MaterialTheme.colorScheme.onSurface)
                    IconButton(onClick = dismiss, modifier = Modifier.semantics { contentDescription = closeDescription }) { Text("✕") }
                }
            },
        ) { dismiss, safeAreaPadding ->
            Column(Modifier.fillMaxWidth().padding(safeAreaPadding)) {
                LazyRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.pending, key = { it.uri }, contentType = { "candidate" }) { candidate ->
                        CandidateTile(
                            uri = candidate.uri, name = candidate.name, imported = candidate.uri in imported,
                            checked = candidate in selected, enabled = ready,
                            onClick = { viewModel.toggleCandidate(document?.id, candidate) },
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { dismiss(); viewModel.ignorePending() }, enabled = ready) { Text(stringResource(R.string.ignore)) }
                    Spacer(Modifier.weight(1f))
                    if (state.access == PhotoAccess.Full) {
                        val mode = state.importMode ?: ImportMode.AddOnly
                        val modeDescription = stringResource(R.string.import_mode_current, stringResource(importModeTitle(mode)))
                        val modeEnabled = ready && state.importMode != null && !state.importModeSaving
                        IconButton(onClick = { dismiss(); onImportMode() }, enabled = modeEnabled,
                            modifier = Modifier.semantics { contentDescription = modeDescription }) {
                            ImportModeIcon(mode, enabled = modeEnabled)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Button(onClick = { if (onCandidates(document?.id, selected.toList())) dismiss() },
                        enabled = selected.isNotEmpty() && ready && state.importMode != null && !state.importModeSaving) {
                        Text(stringResource(R.string.add))
                    }
                }
            }
        }
    }
    state.candidateImportConfirmation?.let { confirmation ->
        if (active) AlertDialog(
            onDismissRequest = viewModel::dismissCandidateImportConfirmation,
            title = { Text(stringResource(R.string.import_delete_batch_title)) },
            text = { Text(stringResource(R.string.import_delete_batch_message, confirmation.request.selected.size, confirmation.request.batch.size)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmCandidateImport, enabled = !state.busy && !state.loading) {
                    Text(stringResource(R.string.import_add_and_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissCandidateImportConfirmation) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (active && guide != null && guideAnchor != null && guide.documentId == document?.id &&
        (guide.kind != NodeGuideKind.Menu || guideNodeId != null)
    ) NodeGuideOverlay(
        anchor = guideAnchor, kind = guide.kind, saving = state.nodeGuideSaving, saveFailed = state.nodeGuideSaveFailed,
        onAcknowledge = viewModel::acknowledgeNodeGuide, onDismiss = viewModel::dismissNodeGuide,
    )
    if (active && guide == null && state.permissionPromptVisible) AlertDialog(
        onDismissRequest = viewModel::dismissPermissionPrompt,
        title = { Text(stringResource(R.string.grant_photo_access_title)) },
        text = { Text(stringResource(R.string.grant_photo_access_message)) },
        confirmButton = { TextButton(onClick = viewModel::requestImagePermission, enabled = !state.permissionPromptSaving) { Text(stringResource(R.string.grant_permission)) } },
        dismissButton = { TextButton(onClick = viewModel::dismissPermissionPrompt, enabled = !state.permissionPromptSaving) { Text(stringResource(R.string.not_now)) } },
    )
    if (active && deleteId != null) AlertDialog(
        onDismissRequest = { deleteId = null }, title = { Text(stringResource(R.string.delete_document), color = MaterialTheme.colorScheme.error) },
        text = { Text(stringResource(R.string.delete_document_message, state.documents.firstOrNull { it.id == deleteId }?.title.orEmpty())) },
        confirmButton = { TextButton(onClick = { viewModel.deleteDocument(deleteId!!); deleteId = null }, enabled = ready) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deleteId = null }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun BluetoothShareMenuItem(onClick: () -> Unit, enabled: Boolean) {
    DropdownMenuItem(
        text = { Text(stringResource(R.string.bluetooth_share), style = ComposeTypography.Font16) },
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 4.dp).clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        leadingIcon = { Icon(painterResource(R.drawable.ic_bluetooth), contentDescription = null) },
        enabled = enabled,
        colors = MenuDefaults.itemColors(
            textColor = MaterialTheme.colorScheme.onPrimaryContainer,
            leadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

@Composable
private fun HomeSideTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tabs.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .selectable(selected = isSelected, enabled = enabled, role = Role.Tab, onClick = { onTabSelected(index) })
                    .heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(3.dp).height(20.dp).clip(RoundedCornerShape(2.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
                Text(label, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = if (isSelected) ComposeTypography.Font16M else ComposeTypography.Font16,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 待选缩略图：选中用 30% 白色覆盖表示，已加入当前文档的图片不再可选。 */
@Composable
private fun CandidateTile(uri: String, name: String, imported: Boolean, checked: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val description = stringResource(R.string.image_named, name)
    Box(Modifier.size(84.dp).clip(RoundedCornerShape(8.dp))
        .semantics { selected = checked }
        .clickable(enabled = enabled && !imported, onClick = onClick)) {
        LocalImage(uri, description, Modifier.fillMaxSize(), crop = true)
        if (imported) {
            Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)))
            Text(stringResource(R.string.already_added), Modifier.align(Alignment.Center), style = ComposeTypography.Font12MWhite)
        } else if (checked) {
            Box(Modifier.matchParentSize().background(Color.White.copy(alpha = 0.3f)))
        }
    }
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
