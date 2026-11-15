package com.allan.picnote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.allan.picnote.data.repositories.DocumentRepository
import com.au.module_androiduiex.picker.rememberComposePhotoPicker
import com.au.module_androiduiex.styles.composeOffsetBlurShadow
import com.au.module_androiduiex.styles.ComposeTextButton as TextButton
import com.au.module_androiduiex.styles.ComposeIconButton as IconButton
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    repository: DocumentRepository,
    onBack: () -> Unit,
    onSaved: (String, Boolean) -> Unit,
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val route = viewModel.route
    val creatingDocument = route.documentId == null
    val titleNode = state.kind == "title"
    val chapter = state.kind == "chapter"
    val introduction = state.kind == "introduction"
    var discard by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var nodeMenu by remember { mutableStateOf(false) }
    var replaceImageId by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberComposePhotoPicker { uris ->
        if (uris.isNotEmpty()) viewModel.addImages(uris.map { it.toString() })
    }
    val replacer = rememberComposePhotoPicker { uris ->
        val id = replaceImageId
        replaceImageId = null
        val index = viewModel.stateFlow.value.images.indexOfFirst { it.id == id }
        val uri = uris.firstOrNull()
        if (uri != null && index >= 0) viewModel.replaceImage(index, uri.toString())
    }
    val addImages = {
        try { picker.launch() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { viewModel.imagePickerFailed(e) }
    }
    val close = { if (!state.busy && state.savedId == null) { if (state.dirty) discard = true else onBack() } }
    val editable = state.loaded && !state.loading && !state.busy && state.savedId == null
    val backState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(state = backState, isBackEnabled = state.dirty || state.busy || state.savedId != null, onBackCompleted = close)
    LaunchedEffect(state.savedId) {
        state.savedId?.let { onSaved(it, state.mirrorFailed) }
    }
    val valid = when {
        creatingDocument || titleNode || chapter -> state.title.trim().isNotEmpty()
        introduction -> state.fixed || state.body.isNotBlank()
        else -> state.body.isNotBlank() || state.images.isNotEmpty()
    }
    val save = { if (route.nodeId != null && !state.dirty) onBack() else viewModel.save() }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(when {
                    creatingDocument -> "新建文档"
                    route.nodeId != null -> "节点编辑"
                    introduction -> "创建引言"
                    chapter -> "创建章节标题"
                    else -> "创建正文"
                }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = close, enabled = !state.busy && state.savedId == null) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (!creatingDocument && state.loaded && !state.fixed && (state.kind == "body" || route.nodeId != null)) Box {
                        IconButton(onClick = { nodeMenu = true }, enabled = editable,
                            modifier = Modifier.semantics { contentDescription = "节点操作" }) { Text("⋯") }
                        DropdownMenu(expanded = nodeMenu, onDismissRequest = { nodeMenu = false }) {
                            if (state.kind == "body") DropdownMenuItem(text = { Text("添加图片") }, onClick = { nodeMenu = false; addImages() })
                            if (route.nodeId != null) DropdownMenuItem(text = { Text("删除节点") }, onClick = { nodeMenu = false; deleting = true })
                        }
                    }
                    TextButton(onClick = save, enabled = valid && editable) { Text(if (creatingDocument) "创建" else "保存") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
            state = rememberLazyListState(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (state.loading || state.busy) item(key = "progress") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (creatingDocument || titleNode || chapter) item(key = "title-input") {
                OutlinedTextField(
                    value = state.title, onValueChange = viewModel::setTitle, enabled = editable,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), singleLine = true,
                    label = { Text(if (chapter) "章节标题" else "标题") },
                )
            }
            if (!creatingDocument && state.kind == "body") {
                itemsIndexed(state.images, key = { _, draft -> draft.id }) { index, draft ->
                    val existing = draft.existing
                    val source = existing?.fileName ?: draft.prepared?.file?.path
                    var ratio by rememberSaveable(source) { mutableStateOf(existing?.let { it.width.toFloat() / it.height }
                        ?: draft.prepared?.let { it.width.toFloat() / it.height } ?: 1f) }
                    val model: Any? = if (existing != null) repository.imageFile(route.documentId!!, existing.fileName) else draft.prepared?.file
                    Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)
                        .composeOffsetBlurShadow(cornerRadius = 2.dp, blurRadius = 16.dp, offsetY = 6.dp,
                            shadowColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.25f))
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(2.dp))) {
                        LocalImage(model, "图片 ${index + 1}", Modifier.fillMaxWidth().aspectRatio(ratio), cacheKey = draft.id,
                            onSize = { width, height -> if (width > 0 && height > 0) ratio = width.toFloat() / height })
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            var menu by remember { mutableStateOf(false) }
                            IconButton(onClick = { menu = true }, enabled = editable,
                                modifier = Modifier.semantics { contentDescription = "图片 ${index + 1} 的操作" }) { Text("⋯") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("替换图片 ${index + 1}") }, onClick = {
                                    menu = false
                                    if (replaceImageId == null) {
                                        replaceImageId = draft.id
                                        try { replacer.launch(maxItems = 1) }
                                        catch (e: CancellationException) { replaceImageId = null; throw e }
                                        catch (e: Exception) { replaceImageId = null; viewModel.imagePickerFailed(e) }
                                    }
                                })
                                DropdownMenuItem(text = { Text("移除图片 ${index + 1}") }, onClick = {
                                    menu = false
                                    viewModel.removeImage(index)
                                })
                            }
                        }
                    }
                }
            }
            if (creatingDocument || introduction || state.kind == "body") item(key = "body-input") {
                OutlinedTextField(
                    value = state.body, onValueChange = viewModel::setBody, enabled = editable,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).heightIn(min = if (creatingDocument) 180.dp else 240.dp),
                    label = { Text(when { creatingDocument -> "引言（可选）"; introduction -> "引言"; state.images.isEmpty() -> "文字内容"; else -> "备注" }) },
                )
            }
            state.error?.let { error -> item(key = "error") { Text(error, Modifier.padding(24.dp), color = MaterialTheme.colorScheme.error) } }
            if (!state.loaded && !state.loading) item(key = "load-retry") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = viewModel::load) { Text("重试读取节点") }
                }
            }
        }
    }
    if (discard) AlertDialog(
        onDismissRequest = { discard = false }, title = { Text("放弃未保存的修改？") },
        confirmButton = { TextButton(onClick = { discard = false; onBack() }) { Text("放弃修改") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("继续编辑") } },
    )
    if (deleting && !state.fixed) AlertDialog(
        onDismissRequest = { deleting = false }, title = { Text("删除节点？") },
        text = { Text(if (chapter) "仅删除此章节标题，其后的其他节点将保留。" else "将删除此节点已保存的文字和 ${state.savedImageCount} 张图片引用，当前草稿也将丢弃，无法恢复。") },
        confirmButton = { TextButton(onClick = { deleting = false; viewModel.deleteNode() }, enabled = editable) { Text("删除节点") } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("保留节点") } },
    )
}
