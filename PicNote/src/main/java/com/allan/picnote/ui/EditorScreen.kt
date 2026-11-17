package com.allan.picnote.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.allan.picnote.R
import com.allan.picnote.data.repositories.DocumentRepository
import com.au.module_androiduiex.landscape.SecondaryPageScaffold
import com.au.module_androiduiex.picker.rememberComposePhotoPicker
import com.au.module_androiduiex.styles.composeOffsetBlurShadow
import com.au.module_androiduiex.styles.ComposeTextButton as TextButton
import androidx.compose.material3.IconButton as ImageIconButton
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
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val route = viewModel.route
    val creatingDocument = route.documentId == null
    val titleNode = state.kind == "title"
    val chapter = state.kind == "chapter"
    val introduction = state.kind == "introduction"
    val imageScroll = rememberLazyListState()
    var discard by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var replaceImageId by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberComposePhotoPicker { uris ->
        if (uris.isNotEmpty()) viewModel.addImages(uris.map { it.toString() })
    }
    val replacer = rememberComposePhotoPicker { uris ->
        val id = replaceImageId
        replaceImageId = null
        val uri = uris.firstOrNull()
        if (uri != null && id != null) viewModel.replaceImage(id, uri.toString())
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
        else -> state.body.isNotBlank() || state.images.any { !it.pendingRemoval }
    }
    val save = { if (route.nodeId != null && !state.dirty) onBack() else viewModel.save() }
    val editorTitle = stringResource(when {
        creatingDocument -> R.string.new_document
        route.nodeId != null -> R.string.edit
        introduction -> R.string.create_introduction
        chapter -> R.string.create_chapter
        else -> R.string.create_body
    })
    val saveLabel = stringResource(if (creatingDocument) R.string.create else R.string.save)
    val canDelete = !creatingDocument && state.loaded && !state.fixed && route.nodeId != null
    val imageActionSize = 32.dp
    val imageContentStartPadding = 24.dp + imageActionSize
    val bodyContentStartPadding = if (landscape && !creatingDocument && state.kind == "body" && state.images.isNotEmpty()) imageContentStartPadding else 24.dp
    SecondaryPageScaffold(
        title = editorTitle,
        onBack = close,
        backContentDescription = stringResource(R.string.back),
        backEnabled = !state.busy && state.savedId == null,
        titleStyle = MaterialTheme.typography.titleLarge,
        primaryActionLabel = saveLabel,
        onPrimaryAction = save,
        primaryActionEnabled = valid && editable,
        loading = state.loading || state.busy,
        actions = {
            if (canDelete) {
                TextButton(onClick = { deleting = true }, enabled = editable) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            }
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = imageScroll,
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
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
                    Row(Modifier.fillMaxWidth().padding(top = 16.dp, end = 24.dp, bottom = 16.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.width(imageContentStartPadding), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(20.dp).border(1.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center) {
                                Text((index + 1).toString(), style = MaterialTheme.typography.labelMedium,
                                    fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1, softWrap = false)
                            }
                            ImageIconButton(onClick = { viewModel.moveImage(draft.id, -1) }, enabled = editable && index > 0,
                                modifier = Modifier.size(imageActionSize)) {
                                Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.move_up))
                            }
                            ImageIconButton(onClick = { viewModel.moveImage(draft.id, 1) }, enabled = editable && index < state.images.lastIndex,
                                modifier = Modifier.size(imageActionSize)) {
                                Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.move_down))
                            }
                            ImageIconButton(onClick = {
                                if (replaceImageId == null) {
                                    replaceImageId = draft.id
                                    try { replacer.launch(maxItems = 1) }
                                    catch (e: CancellationException) { replaceImageId = null; throw e }
                                    catch (e: Exception) { replaceImageId = null; viewModel.imagePickerFailed(e) }
                                }
                            }, enabled = editable, modifier = Modifier.size(imageActionSize)) {
                                Icon(Icons.Default.Refresh, stringResource(R.string.replace_image))
                            }
                            ImageIconButton(onClick = { viewModel.toggleImageRemoval(draft.id) }, enabled = editable,
                                modifier = Modifier.size(imageActionSize),
                                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                                Icon(Icons.Default.Delete, stringResource(if (draft.pendingRemoval) R.string.restore_image else R.string.remove_image))
                            }
                        }
                        Box(Modifier.weight(1f)
                            .composeOffsetBlurShadow(cornerRadius = 2.dp, blurRadius = 16.dp, offsetY = 6.dp,
                                shadowColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.25f))
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(2.dp))) {
                            LocalImage(model, "图片 ${index + 1}", Modifier.fillMaxWidth().aspectRatio(ratio), cacheKey = draft.id,
                                onSize = { width, height -> if (width > 0 && height > 0) ratio = width.toFloat() / height })
                            if (draft.pendingRemoval) {
                                Box(Modifier.matchParentSize().background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(2.dp)))
                            }
                        }
                    }
                }
            }
            if (!creatingDocument && state.kind == "body") item(key = "add-image-button") {
                Box(Modifier.fillMaxWidth().padding(start = bodyContentStartPadding, end = 24.dp, bottom = 12.dp), contentAlignment = Alignment.Center) {
                    TextButton(onClick = addImages, enabled = editable) { Text(stringResource(R.string.add_image), fontSize = 16.sp) }
                }
            }
            if (creatingDocument || introduction || state.kind == "body") item(key = "body-input") {
                OutlinedTextField(
                    value = state.body, onValueChange = viewModel::setBody, enabled = editable,
                    modifier = Modifier.fillMaxWidth().padding(start = bodyContentStartPadding, end = 24.dp).heightIn(min = if (creatingDocument) 180.dp else 240.dp),
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
        onDismissRequest = { deleting = false }, title = { Text(stringResource(R.string.delete_node_question), color = MaterialTheme.colorScheme.error) },
        text = { Text(if (chapter) stringResource(R.string.delete_chapter_editor_message) else stringResource(R.string.delete_node_editor_message, state.savedImageCount)) },
        confirmButton = { TextButton(onClick = { deleting = false; viewModel.deleteNode() }, enabled = editable) { Text(stringResource(R.string.confirm), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } },
    )
}
