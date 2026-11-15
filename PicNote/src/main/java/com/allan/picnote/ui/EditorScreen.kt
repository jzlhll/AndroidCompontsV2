package com.allan.picnote.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.allan.picnote.data.repositories.DocumentRepository
import com.au.module_androiduiex.styles.ComposeBackdropBlurState
import com.au.module_androiduiex.styles.ComposeBackdropBlurWhiteBlock
import com.au.module_androiduiex.styles.ComposeTextButton as TextButton
import com.au.module_androiduiex.styles.ComposeIconButton as IconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    repository: DocumentRepository,
    backdrop: ComposeBackdropBlurState,
    onBack: () -> Unit,
    onSaved: (String, Boolean) -> Unit,
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val route = viewModel.route
    val creatingDocument = route.documentId == null
    val introduction = state.kind == "introduction"
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var discard by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var nodeMenu by remember { mutableStateOf(false) }
    var replaceImageId by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) viewModel.addImages(uris.map { it.toString() })
    }
    val replacer = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val id = replaceImageId
        replaceImageId = null
        val index = viewModel.stateFlow.value.images.indexOfFirst { it.id == id }
        if (uri != null && index >= 0) viewModel.replaceImage(index, uri.toString())
    }
    val addImages = {
        picker.launch(PickVisualMediaRequest.Builder().setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly).setOrderedSelection(true).build())
    }
    val leave = { focusManager.clearFocus(force = true); keyboard?.hide(); onBack() }
    val close = { if (!state.busy && state.savedId == null) { if (state.dirty) discard = true else leave() } }
    val editable = state.loaded && !state.loading && !state.busy && state.savedId == null
    val backState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(state = backState, isBackEnabled = !creatingDocument || state.dirty || state.busy || state.savedId != null, onBackCompleted = close)
    LaunchedEffect(creatingDocument) { if (!creatingDocument) focusManager.clearFocus(force = true) }
    LaunchedEffect(state.savedId) { state.savedId?.let { focusManager.clearFocus(force = true); keyboard?.hide(); onSaved(it, state.mirrorFailed) } }
    val valid = when {
        creatingDocument || route.chapter -> state.title.trim().isNotEmpty()
        else -> state.body.isNotBlank() || state.images.isNotEmpty() || route.nodeId != null
    }
    val save = {
        when {
            route.nodeId != null && !state.dirty -> leave()
            route.nodeId != null && !route.chapter && state.images.isEmpty() && state.body.isBlank() -> deleting = true
            else -> viewModel.save()
        }
    }
    val panel: @Composable () -> Unit = {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = if (creatingDocument) MaterialTheme.colorScheme.background else Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = if (creatingDocument) ScaffoldDefaults.contentWindowInsets else WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = if (creatingDocument) TopAppBarDefaults.windowInsets else WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
                title = { Text(when { creatingDocument -> "新建文档"; introduction -> "编辑引言"; route.chapter -> if (route.nodeId == null) "创建章节" else "编辑章节"; else -> "节点编辑" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { TextButton(onClick = close, enabled = !state.busy && state.savedId == null) { Text("关闭") } },
                actions = {
                    if (!creatingDocument && (!route.chapter || route.nodeId != null)) Box {
                        IconButton(onClick = { nodeMenu = true }, enabled = editable) { Text("⋯") }
                        DropdownMenu(expanded = nodeMenu, onDismissRequest = { nodeMenu = false }) {
                            if (!route.chapter && !introduction) DropdownMenuItem(text = { Text("添加图片") }, onClick = { nodeMenu = false; addImages() })
                            if (route.nodeId != null) DropdownMenuItem(text = { Text("删除节点") }, onClick = { nodeMenu = false; deleting = true })
                        }
                    }
                    TextButton(onClick = save, enabled = valid && editable) { Text(if (creatingDocument) "创建文档" else "保存") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
            .then(if (creatingDocument) Modifier.imePadding() else Modifier)
            .verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            if (state.loading || state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (creatingDocument || route.chapter) {
                OutlinedTextField(
                    value = state.title, onValueChange = viewModel::setTitle, enabled = editable,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = if (creatingDocument) 24.dp else 16.dp, vertical = 16.dp), singleLine = true,
                    label = { Text(if (creatingDocument) "标题" else "章节标题") },
                )
            }
            if (!creatingDocument && !route.chapter && !introduction) {
                state.images.forEachIndexed { index, draft -> key(draft.id) {
                    val existing = draft.existing
                    val source = existing?.fileName ?: draft.prepared?.file?.path
                    var ratio by rememberSaveable(source) { mutableStateOf(existing?.let { it.width.toFloat() / it.height } ?: draft.prepared?.let { it.width.toFloat() / it.height } ?: 1f) }
                    val model: Any? = if (existing != null) repository.imageFile(route.documentId!!, existing.fileName) else draft.prepared?.file
                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)
                        .shadow(16.dp, RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                        LocalImage(model, draft.name.ifBlank { "图片 ${index + 1}" }, Modifier.fillMaxWidth().aspectRatio(ratio), cacheKey = draft.id,
                            onSize = { width, height -> if (width > 0 && height > 0) ratio = width.toFloat() / height })
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(draft.name, onValueChange = { viewModel.imageName(index, it) }, modifier = Modifier.weight(1f),
                            enabled = editable, singleLine = true, label = { Text("图片说明") })
                        Box {
                            var menu by remember { mutableStateOf(false) }
                            IconButton(onClick = { menu = true }, enabled = editable) { Text("⋯") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("替换图片") }, onClick = {
                                    menu = false
                                    replaceImageId = draft.id
                                    replacer.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                })
                                DropdownMenuItem(text = { Text("移除图片") }, onClick = {
                                    menu = false
                                    if (route.nodeId != null && state.images.size == 1 && state.body.isBlank()) deleting = true
                                    else viewModel.removeImage(index)
                                })
                            }
                        }
                    }
                } }
            }
            if (!route.chapter) OutlinedTextField(
                value = state.body, onValueChange = viewModel::setBody, enabled = editable,
                modifier = Modifier.fillMaxWidth().padding(horizontal = if (creatingDocument) 24.dp else 16.dp).heightIn(min = if (creatingDocument) 180.dp else 240.dp),
                label = { Text(when { creatingDocument -> "引言（可选）"; introduction -> "引言"; state.images.isEmpty() -> "文字内容"; else -> "备注" }) },
            )
            state.error?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
            if (!state.loaded && !state.loading) TextButton(onClick = viewModel::load, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("重试读取节点") }
        }
    }
    }
    if (creatingDocument) panel()
    else Box(Modifier.fillMaxSize().semantics { isTraversalGroup = true; paneTitle = "节点编辑" }) {
        ComposeBackdropBlurWhiteBlock(
            state = backdrop, modifier = Modifier.matchParentSize(), cornerRadius = 0.dp,
            backgroundBlurRadius = 16.dp, strokeWidth = 0.dp, shadowBlurRadius = 0.dp,
            shadowColor = Color.Transparent, strokeColor = Color.Transparent, backgroundColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.16f),
        ) { }
        Box(Modifier.matchParentSize().clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "关闭节点编辑", onClick = close,
        ))
        Box(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)).imePadding()
                .padding(horizontal = 24.dp, vertical = 24.dp), contentAlignment = Alignment.Center,
        ) {
            ComposeBackdropBlurWhiteBlock(
                state = backdrop,
                modifier = Modifier.fillMaxWidth().heightIn(max = 720.dp).fillMaxHeight()
                    .pointerInput(Unit) { detectTapGestures(onTap = { }) },
                cornerRadius = 24.dp, backgroundBlurRadius = 16.dp, shadowBlurRadius = 24.dp,
                backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                strokeColor = MaterialTheme.colorScheme.outlineVariant,
                shadowColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.2f),
            ) { panel() }
        }
    }
    if (discard) AlertDialog(
        onDismissRequest = { discard = false }, title = { Text("放弃未保存的修改？") },
        confirmButton = { TextButton(onClick = { discard = false; leave() }) { Text("放弃修改") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("继续编辑") } },
    )
    if (deleting) AlertDialog(
        onDismissRequest = { deleting = false }, title = { Text("删除节点？") },
        text = { Text(if (route.chapter) "仅删除此章节标题，其后的其他节点将保留。" else "将删除此节点已保存的文字和 ${state.savedImageCount} 张图片引用，当前草稿也将丢弃，无法恢复。") },
        confirmButton = { TextButton(onClick = { deleting = false; viewModel.deleteNode() }, enabled = editable) { Text("删除节点") } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("保留节点") } },
    )
}
