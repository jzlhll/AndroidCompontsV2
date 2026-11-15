package com.allan.picnote.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.allan.picnote.R
import com.allan.picnote.data.WelcomeDocumentId
import com.allan.picnote.logEx
import com.allan.picnote.media.PhotoAccess
import com.allan.picnote.media.photoAccess
import com.allan.picnote.media.photoPermissions
import com.allan.picnote.ui.components.CreateDocumentButton
import com.au.module_androiduiex.styles.ComposeTheme
import com.au.module_androiduiex.styles.ComposeLineTabLayout
import com.au.module_androiduiex.styles.ComposeLineTabLayoutStyle
import com.au.module_androiduiex.styles.ComposeTypography
import com.au.module_androiduiex.styles.ComposeButton as Button
import com.au.module_androiduiex.styles.ComposeTextButton as TextButton
import com.au.module_androiduiex.styles.ComposeIconButton as IconButton
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            viewModel.showMessage("无法打开系统设置，请从应用信息中开启图片权限")
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
                    viewModel.showMessage("无法发起图片权限申请，请重试或进入系统设置")
                }
            }
        }
        onPauseOrDispose { }
    }
    LaunchedEffect(state.permissionHistory, state.permissionHistoryLoading, state.permissionHistoryFailed, active) {
        val history = state.permissionHistory
        if (active && !state.permissionHistoryLoading && !state.permissionHistoryFailed && history != null && !history.explanationSeen && !history.requested
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
    val content = state.content
    val document = content?.document
    val showMediaActions = document?.id != WelcomeDocumentId
    val latestPending = state.pending.firstOrNull()
    val showLoading = state.loading || state.busy || state.scanning
    val imported = remember(state.content) { state.content?.nodes.orEmpty().flatMap { it.images }.mapNotNull { it.sourceUri }.toSet() }
    val dateFormatter = remember { SimpleDateFormat("MM-dd HH:mm", Locale.CHINA) }
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
                Row(Modifier.fillMaxWidth().height(84.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("我的文档", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    CreateDocumentButton("＋ 新建", onClick = { scope.launch { drawer.close(); onCreate() } }, enabled = ready)
                }
                LazyColumn(Modifier.weight(1f).selectableGroup()) {
                    items(state.documents, key = { it.id }, contentType = { "document" }) { item ->
                        var menu by remember { mutableStateOf(false) }
                        val updatedAt = remember(item.updatedAt, dateFormatter) { dateFormatter.format(Date(item.updatedAt)) }
                        Row(
                            Modifier.fillMaxWidth().background(if (item.id == document?.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                                .semantics { this.selected = item.id == document?.id }
                                .clickable(enabled = ready) { viewModel.reloadDocument(item.id); scope.launch { drawer.close() } }.padding(start = 20.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${item.nodeCount} 个节点 · $updatedAt",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Box {
                                IconButton(onClick = { menu = true }, enabled = ready,
                                    modifier = Modifier.semantics { contentDescription = "文档「${item.title}」的操作" }) { Text("⋯") }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text("重命名") }, onClick = { menu = false; beginRename(item.id, item.title) })
                                    DropdownMenuItem(text = { Text("删除文档", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; deleteId = item.id })
                                }
                            }
                        }
                    }
                }
                HorizontalDivider()
                TextButton(onClick = { appearanceDialog = true }, enabled = active && !state.themeSaving,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) { Text("外观") }
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    expandedHeight = 84.dp,
                    title = { Text(document?.title ?: stringResource(R.string.app_name), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawer.open() } }, enabled = active,
                            modifier = Modifier.semantics { contentDescription = "打开文档列表" }) { Text("☰") }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { more = true }, enabled = document != null && ready,
                                modifier = Modifier.semantics { contentDescription = "文档操作" }) { Text("⋯") }
                            DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                                if (document != null) {
                                    DropdownMenuItem(text = { Text("重命名", style = ComposeTypography.Font16, color = LocalContentColor.current) },
                                        onClick = { more = false; beginRename(document.id, document.title) }, enabled = ready)
                                    DropdownMenuItem(text = { Text("导出文档", style = ComposeTypography.Font16, color = LocalContentColor.current) },
                                        onClick = { more = false; viewModel.export() }, enabled = ready)
                                    DropdownMenuItem(text = { Text("删除文档", style = ComposeTypography.Font16, color = MaterialTheme.colorScheme.error) },
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
                                        LocalImage(latest.uri, "最新图片：${latest.name}", Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)), crop = true)
                                        Text("新增${state.pending.size}", Modifier.weight(1f, fill = false).padding(end = 8.dp),
                                            style = ComposeTypography.Font14M, color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                        FloatingActionButton(
                            onClick = { if (ready) onPickImages() },
                            modifier = Modifier.semantics { contentDescription = "打开相册添加图片"; if (!ready) disabled() },
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
                                        LocalImage(candidate.uri, "图片：${candidate.name}", Modifier.size(104.dp), crop = true)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(checked = candidate.uri in selected, onCheckedChange = null, enabled = !disabled)
                                            if (candidate.uri in imported) Text("已添加", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { viewModel.ignoreCandidates(selected); selected = emptyList() }, enabled = selected.isNotEmpty() && ready) { Text("忽略") }
                                Button(onClick = { onCandidates(selected); selected = emptyList() }, enabled = selected.isNotEmpty() && ready) { Text("添加 ${selected.size} 张") }
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
                    Box(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        ComposeLineTabLayout(
                            tabs = listOf("节点", "文档"), selectedIndex = pager.currentPage,
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
                        Text("已保存，文档镜像更新失败", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = viewModel::retryMirror, enabled = ready) { Text("重试") }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (content != null) {
                            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1, key = { it }) { page ->
                                if (page == 0) NodeList(
                                    content = content, repository = viewModel.repository, listState = nodeScroll, enabled = ready, active = active && pager.currentPage == 0,
                                    onEdit = { node -> onEdit(EditRoute(content.document.id, node.node.id, kind = node.node.kind)) },
                                    onInsert = { anchor, kind -> onEdit(EditRoute(content.document.id, anchorId = anchor, kind = kind)) },
                                    onMove = viewModel::move, onDelete = viewModel::deleteNode, onMerge = viewModel::mergeNodes,
                                    bottomContentPadding = if (showMediaActions) 96.dp else 24.dp,
                                ) else DocumentPreview(content, viewModel.repository, documentScroll, onMessage = viewModel::showMessage,
                                    active = active && pager.currentPage == 1, bottomContentPadding = if (showMediaActions) 96.dp else 24.dp)
                            }
                        }
                    }
                }
            }
        }
    }
    if (active && state.permissionPromptVisible) AlertDialog(
        onDismissRequest = viewModel::dismissPermissionPrompt,
        title = { Text("授予图片访问权限") },
        text = { Text("授权访问全部图片后，每次进入会自动提醒新增图片，方便您的使用。\n如果不授权，则可以每次手动选择添加图片。") },
        confirmButton = { TextButton(onClick = viewModel::requestImagePermission, enabled = !state.permissionPromptSaving) { Text("授予权限") } },
        dismissButton = { TextButton(onClick = viewModel::dismissPermissionPrompt, enabled = !state.permissionPromptSaving) { Text("暂不授权") } },
    )
    if (active && renameId != null) AlertDialog(
        onDismissRequest = { renameId = null }, title = { Text("重命名文档") },
        text = { OutlinedTextField(value = renameTitle, onValueChange = { renameTitle = it }, singleLine = true, label = { Text("标题") }) },
        confirmButton = { TextButton(onClick = { viewModel.rename(renameId!!, renameTitle); renameId = null }, enabled = renameTitle.trim().isNotEmpty() && ready) { Text("保存") } },
        dismissButton = { TextButton(onClick = { renameId = null }) { Text("取消") } },
    )
    if (active && deleteId != null) AlertDialog(
        onDismissRequest = { deleteId = null }, title = { Text("删除文档", color = MaterialTheme.colorScheme.error) },
        text = { Text("将删除「${state.documents.firstOrNull { it.id == deleteId }?.title.orEmpty()}」的所有节点、文字和本地图片副本，无法恢复。") },
        confirmButton = { TextButton(onClick = { viewModel.deleteDocument(deleteId!!); deleteId = null }, enabled = ready) { Text("删除", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deleteId = null }) { Text("取消") } },
    )
    if (active && appearanceDialog) AlertDialog(
        onDismissRequest = { appearanceDialog = false }, title = { Text("外观") },
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
        confirmButton = { TextButton(onClick = { appearanceDialog = false }) { Text("关闭") } },
    )
}

@Composable
private fun PermissionBanner(state: HomeState, onPermission: () -> Unit, active: Boolean) {
    if (state.access == PhotoAccess.Full && state.scanError == null) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        if (state.access != PhotoAccess.Full) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (state.access == PhotoAccess.Partial) "图片权限：部分图片" else "图片权限：未授权",
                Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            IconButton(onClick = onPermission,
                enabled = active && !state.permissionHistoryLoading && !state.permissionPromptSaving && !state.permissionRequestPending,
                modifier = Modifier.semantics { contentDescription = "管理图片访问权限" }) {
                Text("›", style = MaterialTheme.typography.titleLarge)
            }
        }
        if (state.scanError != null) Text(state.scanError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
