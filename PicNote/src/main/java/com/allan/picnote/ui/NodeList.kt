package com.allan.picnote.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.allan.picnote.data.entity.DocumentContent
import com.allan.picnote.data.entity.NodeContent
import com.allan.picnote.data.repositories.DocumentRepository
import com.au.module_androiduiex.styles.ComposeTextButton as TextButton
import com.au.module_androiduiex.styles.ComposeIconButton as IconButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun NodeList(
    content: DocumentContent,
    repository: DocumentRepository,
    listState: LazyListState,
    enabled: Boolean,
    active: Boolean,
    onEdit: (NodeContent) -> Unit,
    onInsert: (String, Boolean, Boolean) -> Unit,
    onMove: (String, String?) -> Unit,
    onDelete: (String) -> Unit,
    onEmptyAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var deleting by remember { mutableStateOf<NodeContent?>(null) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragCenter by remember { mutableFloatStateOf(0f) }
    var beforeId by remember { mutableStateOf<String?>(null) }
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    if (content.nodes.isEmpty()) Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        TextButton(onClick = onEmptyAdd, enabled = enabled) { Text("添加第一个节点") }
    } else LazyColumn(state = listState, modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(start = 4.dp, end = 16.dp, top = 16.dp, bottom = 24.dp)) {
        items(content.nodes, key = { it.node.id }) { item ->
            val index = content.nodes.indexOfFirst { it.node.id == item.node.id }
            var menu by remember { mutableStateOf(false) }
            val dragModifier = Modifier.pointerInput(item.node.id, enabled, content.nodes) {
                if (enabled) detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragging = item.node.id
                        dragOffset = 0f
                        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == item.node.id }
                        dragCenter = if (info == null) 0f else info.offset + info.size / 2f
                        beforeId = content.nodes.getOrNull(index + 1)?.node?.id
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragOffset += amount.y
                        dragCenter += amount.y
                        val others = listState.layoutInfo.visibleItemsInfo.filter { it.key != item.node.id }
                        beforeId = others.firstOrNull { it.offset + it.size / 2f > dragCenter }?.key as? String
                            ?: content.nodes.getOrNull((others.lastOrNull()?.index ?: content.nodes.lastIndex) + 1)?.node?.id
                    },
                    onDragEnd = {
                        onMove(item.node.id, beforeId)
                        dragging = null
                        dragOffset = 0f
                    },
                    onDragCancel = { dragging = null; dragOffset = 0f },
                )
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp).drawBehind {
                drawLine(lineColor, Offset(24.dp.toPx(), -12.dp.toPx()), Offset(24.dp.toPx(), size.height), 1.dp.toPx())
            }
                .zIndex(if (dragging == item.node.id) 1f else 0f)
                .graphicsLayer { translationY = if (dragging == item.node.id) dragOffset else 0f }, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(48.dp).height(54.dp), contentAlignment = Alignment.Center) {
                    IconButton(onClick = { menu = true }, enabled = enabled, modifier = Modifier.semantics { contentDescription = "节点操作" }) {
                        Text("●", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("插入上一个节点") }, onClick = { menu = false; onInsert(item.node.id, false, false) })
                        DropdownMenuItem(text = { Text("插入下一个节点") }, onClick = { menu = false; onInsert(item.node.id, true, false) })
                        DropdownMenuItem(text = { Text("创建章节 · 插在前面") }, onClick = { menu = false; onInsert(item.node.id, false, true) })
                        DropdownMenuItem(text = { Text("创建章节 · 插在后面") }, onClick = { menu = false; onInsert(item.node.id, true, true) })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("编辑") }, onClick = { menu = false; onEdit(item) })
                        DropdownMenuItem(text = { Text("上移") }, enabled = index > 0, onClick = { menu = false; onMove(item.node.id, content.nodes[index - 1].node.id) })
                        DropdownMenuItem(text = { Text("下移") }, enabled = index < content.nodes.lastIndex, onClick = { menu = false; onMove(item.node.id, content.nodes.getOrNull(index + 2)?.node?.id) })
                        DropdownMenuItem(text = { Text("删除节点") }, onClick = { menu = false; deleting = item })
                    }
                }
                if (item.images.isEmpty()) {
                    Column(Modifier.weight(1f).height(72.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                        .then(dragModifier).clickable(enabled = enabled, onClickLabel = when (item.node.kind) { "chapter" -> "编辑章节"; "introduction" -> "编辑引言"; else -> "编辑文字" }) { onEdit(item) }.padding(horizontal = 14.dp), verticalArrangement = Arrangement.Center) {
                        if (item.node.kind == "chapter") {
                            Text(item.node.title, modifier = Modifier.clearAndSetSemantics { contentDescription = "章节：${item.node.title}"; heading() },
                                maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        } else Text(rememberCompactMarkdown(item.node.bodyMarkdown),
                            modifier = if (item.node.kind == "introduction") Modifier.padding(start = 24.dp) else Modifier,
                            color = if (item.node.kind == "introduction") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                } else ImageNodeCard(item, repository, enabled, active, onEdit = { onEdit(item) }, modifier = Modifier.weight(1f).then(dragModifier))
            }
        }
    }
    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null }, title = { Text("删除节点") },
            text = { Text(if (item.node.kind == "chapter") "仅删除章节标题「${item.node.title}」，其后的节点将保留。" else "将删除此节点的文字和 ${item.images.size} 张图片引用，无法恢复。") },
            confirmButton = { TextButton(onClick = { onDelete(item.node.id); deleting = null }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun ImageNodeCard(
    content: NodeContent,
    repository: DocumentRepository,
    enabled: Boolean,
    active: Boolean,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pager = rememberPagerState(pageCount = { content.images.size })
    val alpha = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var visibilityJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(active) { if (!active) { visibilityJob?.cancel(); alpha.snapTo(0f) } }
    DisposableEffect(Unit) { onDispose { visibilityJob?.cancel() } }
    val showSettings = {
        visibilityJob?.cancel()
        visibilityJob = scope.launch {
            alpha.animateTo(1f, tween(500))
            delay(3000.milliseconds)
            alpha.animateTo(0f, tween(500))
        }
    }
    Box(modifier.aspectRatio(4f / 3f).clip(RoundedCornerShape(12.dp)).semantics {
        customActions = listOf(CustomAccessibilityAction("打开图片") { onEdit(); true }, CustomAccessibilityAction("编辑文字") { onEdit(); true })
    }) {
        HorizontalPager(state = pager) { page ->
            val image = content.images[page]
            LocalImage(repository.imageFile(content.node.documentId, image.fileName), image.name.ifBlank { "图片 ${page + 1}" },
                Modifier.fillMaxSize().clickable(enabled = enabled, onClick = showSettings), crop = true)
        }
        if (content.node.bodyMarkdown.isNotBlank()) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(32.dp)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)))).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
            Text(rememberCompactMarkdown(content.node.bodyMarkdown), maxLines = 2, overflow = TextOverflow.Ellipsis, color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
        if (content.images.size > 1) Text("${pager.currentPage + 1}/${content.images.size}", Modifier.align(Alignment.TopStart).padding(8.dp)
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
        if (alpha.value > 0f) IconButton(
            onClick = { visibilityJob?.cancel(); scope.launch { alpha.snapTo(0f) }; onEdit() }, enabled = enabled,
            modifier = Modifier.align(Alignment.TopEnd).graphicsLayer { this.alpha = alpha.value }.semantics { contentDescription = "编辑图片和用户备注" },
        ) { Text("⚙", color = Color.White, modifier = Modifier.background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp)).padding(6.dp)) }
    }
}
