package com.allan.picnote.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.allan.picnote.data.entity.DocumentSummary
import com.allan.picnote.logEx
import com.allan.picnote.media.PhotoAccess
import com.allan.picnote.media.photoAccess
import com.allan.picnote.media.photoPermissions
import com.au.module_androiduiex.styles.ComposeTheme
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
    var addMenu by remember { mutableStateOf(false) }
    var appearanceDialog by rememberSaveable { mutableStateOf(false) }
    val document = state.content?.document
    val imported = state.content?.nodes.orEmpty().flatMap { it.images }.mapNotNull { it.sourceUri }.toSet()
    LaunchedEffect(state.pending, imported) { selected = selected.filter { id -> state.pending.any { it.uri == id } && id !in imported } }
    val beginRename: (DocumentSummary) -> Unit = { renameId = it.id; renameTitle = it.title }

    ModalNavigationDrawer(
        modifier = modifier,
        drawerState = drawer,
        gesturesEnabled = ready,
        drawerContent = {
            ModalDrawerSheet(Modifier.fillMaxWidth(0.86f)) {
                Column(Modifier.fillMaxHeight()) {
                if (state.documents.isEmpty()) {
                    if (state.loading) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(32.dp))
                    } else Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text("空空如也", fontSize = 32.sp, style = MaterialTheme.typography.headlineLarge)
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { scope.launch { drawer.close(); onCreate() } }, enabled = ready) { Text("+新建") }
                    }
                } else {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("我的文档", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { scope.launch { drawer.close(); onCreate() } }, enabled = ready) { Text("＋ 新建") }
                }
                LazyColumn(Modifier.weight(1f)) {
                    items(state.documents, key = { it.id }) { item ->
                        var menu by remember { mutableStateOf(false) }
                        Row(
                            Modifier.fillMaxWidth().background(if (item.id == document?.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                                .clickable(enabled = ready) { viewModel.reloadDocument(item.id); scope.launch { drawer.close() } }.padding(start = 20.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${item.nodeCount} 个节点 · ${SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(item.updatedAt))}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Box {
                                IconButton(onClick = { menu = true }, enabled = ready) { Text("⋯") }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text("重命名") }, onClick = { menu = false; beginRename(item) })
                                    DropdownMenuItem(text = { Text("删除文档") }, onClick = { menu = false; deleteId = item.id })
                                }
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
                    title = { Text(document?.title ?: "图笺", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = { IconButton(onClick = { scope.launch { drawer.open() } }, enabled = active) { Text("☰") } },
                    actions = {
                        Box {
                            IconButton(onClick = { more = true }, enabled = document != null && ready) { Text("⋯") }
                            DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                                DropdownMenuItem(text = { Text("重命名") }, onClick = {
                                    more = false
                                    state.documents.firstOrNull { it.id == document?.id }?.let(beginRename)
                                })
                                DropdownMenuItem(text = { Text("复制 Markdown") }, onClick = { more = false; viewModel.copyMarkdown() })
                                DropdownMenuItem(text = { Text("导出文档") }, onClick = { more = false; viewModel.export() })
                            }
                        }
                    },
                )
            },
            bottomBar = {
                Surface(tonalElevation = 2.dp) {
                    Column(Modifier.navigationBarsPadding()) {
                        if (ribbon && state.pending.isNotEmpty()) {
                            LazyRow(Modifier.fillMaxWidth().padding(top = 12.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(state.pending, key = { it.uri }) { candidate ->
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
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { if (state.pending.isEmpty()) onPickImages() else ribbon = !ribbon }, enabled = ready) {
                                Text(if (state.pending.isEmpty()) "相册" else "新图片 ${state.pending.size}")
                            }
                            Button(onClick = onPickImages, enabled = ready) { Text("＋ 添加图片") }
                        }
                    }
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                PermissionBanner(state, onPermission = requestPermission, active = active)
                if ((state.busy || state.loading) && !state.scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.mirrorFailed) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("已保存，文档镜像更新失败", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = viewModel::retryMirror, enabled = ready) { Text("重试") }
                }
                if (document == null) {
                    Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Button(onClick = onCreate, enabled = ready) { Text("新建文档") }
                    }
                } else documentStates.SaveableStateProvider(document.id) {
                    val pager = rememberPagerState(pageCount = { 2 })
                    val nodeScroll = rememberLazyListState()
                    val documentScroll = rememberLazyListState()
                    TabRow(selectedTabIndex = pager.currentPage) {
                        listOf("节点", "文档").forEachIndexed { index, title ->
                            Tab(selected = pager.currentPage == index, onClick = { scope.launch { pager.animateScrollToPage(index) } }, text = { Text(title) })
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                            if (page == 0) NodeList(
                                content = state.content!!, repository = viewModel.repository, listState = nodeScroll, enabled = ready, active = active && pager.currentPage == 0,
                                onEdit = { node -> onEdit(EditRoute(document.id, node.node.id, chapter = node.node.kind == "chapter")) },
                                onInsert = { anchor, after, chapter -> onEdit(EditRoute(document.id, anchorId = anchor, after = after, chapter = chapter)) },
                                onMove = viewModel::move, onDelete = viewModel::deleteNode,
                                onEmptyAdd = { addMenu = true },
                            ) else DocumentPreview(state.content!!, viewModel.repository, documentScroll, active = active)
                        }
                        Box(Modifier.align(Alignment.Center)) {
                            DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                                DropdownMenuItem(text = { Text("纯文字 / 图文节点") }, onClick = { addMenu = false; onEdit(EditRoute(document.id)) })
                                DropdownMenuItem(text = { Text("章节") }, onClick = { addMenu = false; onEdit(EditRoute(document.id, chapter = true)) })
                                DropdownMenuItem(text = { Text("图片") }, onClick = { addMenu = false; onPickImages() })
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
        text = { Text("授权访问全部图片后，每次进入会检查图片变化。也可不授权，使用系统相册手动选图。") },
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
        onDismissRequest = { deleteId = null }, title = { Text("删除文档") },
        text = { Text("将删除「${state.documents.firstOrNull { it.id == deleteId }?.title.orEmpty()}」的所有节点、文字和本地图片副本，无法恢复。") },
        confirmButton = { TextButton(onClick = { viewModel.deleteDocument(deleteId!!); deleteId = null }, enabled = ready) { Text("删除") } },
        dismissButton = { TextButton(onClick = { deleteId = null }) { Text("取消") } },
    )
    if (active && appearanceDialog) AlertDialog(
        onDismissRequest = { appearanceDialog = false }, title = { Text("外观") },
        text = {
            Column(Modifier.selectableGroup()) {
                ComposeTheme.entries.forEach { theme ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = state.theme == theme, enabled = !state.themeSaving, role = Role.RadioButton,
                        onClick = { viewModel.setTheme(theme); appearanceDialog = false }).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = state.theme == theme, onClick = null, enabled = !state.themeSaving)
                        Text(theme.name)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { appearanceDialog = false }) { Text("关闭") } },
    )
}

@Composable
private fun PermissionBanner(state: HomeState, onPermission: () -> Unit, active: Boolean) {
    val summary = state.scanSummary
    val hasChanges = state.access == PhotoAccess.Full && summary != null && summary.added > 0
    if (state.access == PhotoAccess.Full && state.scanError == null && !state.scanning && !hasChanges) return
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
        when {
            state.scanning -> LinearProgressIndicator(Modifier.fillMaxWidth())
            state.scanError != null -> Text(state.scanError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            hasChanges && summary != null -> Text("新增 ${summary.added} 张图片",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
