package com.allan.picnote.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.allan.picnote.data.entity.DocumentContent
import com.allan.picnote.data.entity.NodeContent
import com.allan.picnote.data.repositories.DocumentRepository
import com.allan.picnote.ui.theme.LocalThemePalette
import com.allan.picnote.ui.theme.StatusColors
import com.au.module_androiduiex.styles.ComposeTypography
import com.au.module_androiduiex.styles.ComposeTextButton as TextButton
import com.au.module_androiduiex.styles.ComposeIconButton as IconButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private fun nodeTypeLabel(kind: String): String = when (kind) {
    "title" -> "标题"
    "introduction" -> "引言"
    "chapter" -> "章节标题"
    else -> "正文"
}

@Composable
fun NodeList(
    content: DocumentContent,
    repository: DocumentRepository,
    listState: LazyListState,
    enabled: Boolean,
    active: Boolean,
    onEdit: (NodeContent) -> Unit,
    onInsert: (String?, String) -> Unit,
    onMove: (String, String?) -> Unit,
    onDelete: (String) -> Unit,
    onMerge: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = 24.dp,
) {
    var deleting by remember(content.document.id) { mutableStateOf<NodeContent?>(null) }
    var merging by remember(content.document.id) { mutableStateOf<Pair<String, String>?>(null) }
    var dragging by remember(content.document.id) { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    var targetId by remember { mutableStateOf<String?>(null) }
    val cardCoordinates = remember(content.document.id) { mutableMapOf<String, LayoutCoordinates>() }
    val previews = rememberNodePreviews(content.nodes)
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val interactive = enabled && active && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(interactive) {
        if (!interactive) { dragging = null; targetId = null; dragOffset = Offset.Zero }
    }
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val palette = LocalThemePalette.current
    LazyColumn(state = listState, modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(start = 4.dp, end = 16.dp, top = 16.dp, bottom = bottomContentPadding)) {
        itemsIndexed(content.nodes, key = { _, item -> item.node.id }, contentType = { _, item ->
            if (item.images.isEmpty()) "text" else "picture"
        }) { index, item ->
            val colors = palette.nodeColors(item.node.kind)
            val fixed = index < 2
            val number = (index + 1).toString().padStart(2, '0')
            DisposableEffect(item.node.id, item.node.kind) { onDispose { cardCoordinates.remove(item.node.id) } }
            val dragModifier = if (item.node.kind != "body") Modifier else Modifier.pointerInput(item.node.id, interactive, content.nodes) {
                if (interactive) detectDragGesturesAfterLongPress(
                    onDragStart = start@ { position ->
                        val coordinates = cardCoordinates[item.node.id] ?: return@start
                        if (!coordinates.isAttached) return@start
                        dragging = item.node.id
                        dragOffset = Offset.Zero
                        dragPosition = coordinates.localToRoot(position)
                        targetId = null
                    },
                    onDrag = drag@ { change, amount ->
                        if (dragging != item.node.id) return@drag
                        change.consume()
                        dragOffset += amount
                        dragPosition += amount
                        targetId = cardCoordinates.entries.firstOrNull { (id, coordinates) ->
                            id != item.node.id && coordinates.isAttached && coordinates.boundsInRoot().contains(dragPosition)
                        }?.key
                    },
                    onDragEnd = {
                        val target = targetId
                        if (dragging == item.node.id && target != null) merging = item.node.id to target
                        dragging = null
                        targetId = null
                        dragOffset = Offset.Zero
                    },
                    onDragCancel = { dragging = null; targetId = null; dragOffset = Offset.Zero },
                )
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp).drawBehind {
                drawLine(lineColor, Offset(24.dp.toPx(), -12.dp.toPx()), Offset(24.dp.toPx(), size.height), 1.dp.toPx())
            }.zIndex(if (dragging == item.node.id) 1f else 0f), verticalAlignment = Alignment.CenterVertically) {
                NodeMenu(
                    number = number, enabled = interactive, foreground = colors.foreground, fixed = fixed,
                    canMoveUp = index > 2, canMoveDown = !fixed && index < content.nodes.lastIndex,
                    onInsert = { onInsert(item.node.id, it) },
                    onMoveUp = { onMove(item.node.id, content.nodes[index - 1].node.id) },
                    onMoveDown = { onMove(item.node.id, content.nodes.getOrNull(index + 2)?.node?.id) },
                    onDelete = { deleting = item },
                )
                NodeCard(item, repository, colors, preview = previews[item.node.bodyMarkdown].orEmpty(),
                    enabled = interactive, dragging = dragging == item.node.id,
                    highlighted = targetId == item.node.id, onEdit = { onEdit(item) },
                    modifier = Modifier.weight(1f).graphicsLayer {
                        translationX = if (dragging == item.node.id) dragOffset.x else 0f
                        translationY = if (dragging == item.node.id) dragOffset.y else 0f
                    }.then(if (item.node.kind == "body") Modifier.onPlaced { cardCoordinates[item.node.id] = it } else Modifier)
                        .then(dragModifier))
            }
        }
        item(key = "insert-tail", contentType = "insert-tail") {
            Row(Modifier.fillMaxWidth().drawBehind {
                drawLine(lineColor, Offset(24.dp.toPx(), -12.dp.toPx()), Offset(24.dp.toPx(), size.height / 2f), 1.dp.toPx())
            }, verticalAlignment = Alignment.CenterVertically) {
                NodeMenu(number = null,
                    enabled = interactive, foreground = MaterialTheme.colorScheme.onSurfaceVariant, fixed = false,
                    canMoveUp = false, canMoveDown = false, tail = true, onInsert = { onInsert(null, it) },
                    onMoveUp = {}, onMoveDown = {}, onDelete = {})
                var menu by remember { mutableStateOf(false) }
                LaunchedEffect(interactive) { if (!interactive) menu = false }
                Box(Modifier.clickable(enabled = interactive, role = Role.Button) { menu = true }
                    .padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Text("＋ 添加节点", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    DropdownMenu(expanded = menu && interactive, onDismissRequest = { menu = false }) {
                        InsertItems { kind -> menu = false; onInsert(null, kind) }
                    }
                }
            }
        }
    }
    if (active) deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null }, title = { Text("删除节点") },
            text = { Text(if (item.node.kind == "chapter") "仅删除章节标题「${item.node.title}」，其后的节点将保留。" else "将删除此节点的文字和 ${item.images.size} 张图片引用，无法恢复。") },
            confirmButton = { TextButton(onClick = { onDelete(item.node.id); deleting = null }, enabled = interactive) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
    if (active) merging?.let { pair ->
        val selected = content.nodes.withIndex().filter { it.value.node.id == pair.first || it.value.node.id == pair.second }
        val valid = selected.size == 2 && selected.all { it.value.node.kind == "body" }
        AlertDialog(
            onDismissRequest = { merging = null }, title = { Text("合并节点") },
            text = { Text(if (valid) {
                val earlier = selected[0]
                val later = selected[1]
                val firstNumber = (earlier.index + 1).toString().padStart(2, '0')
                val secondNumber = (later.index + 1).toString().padStart(2, '0')
                "将合并 $firstNumber（${earlier.value.images.size} 张图片）与 $secondNumber（${later.value.images.size} 张图片）。\n文字和图片均按 $firstNumber、$secondNumber 的顺序保留，文字以换行连接，保留 $firstNumber 的位置。"
            } else "节点已变化，请取消后重新选择。") },
            confirmButton = { TextButton(onClick = { merging = null; onMerge(pair.first, pair.second) }, enabled = valid && interactive) { Text("合并") } },
            dismissButton = { TextButton(onClick = { merging = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun NodeMenu(
    number: String?,
    enabled: Boolean,
    foreground: Color,
    fixed: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onInsert: (String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    tail: Boolean = false,
) {
    var menu by remember { mutableStateOf(false) }
    var inserting by remember { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) { menu = false; inserting = false } }
    Box(modifier.size(48.dp), contentAlignment = Alignment.Center) {
        IconButton(onClick = { inserting = false; menu = true }, enabled = enabled,
            modifier = Modifier.size(48.dp).semantics { contentDescription = if (tail) "添加节点" else "节点操作" }) {
            Box(Modifier.size(8.dp).background(foreground, CircleShape))
        }
        if (number != null) Text(number, Modifier.align(Alignment.TopCenter).offset(y = 32.dp)
            .background(MaterialTheme.colorScheme.background).padding(horizontal = 2.dp),
            style = MaterialTheme.typography.labelSmall, color = foreground, maxLines = 1, softWrap = false)
        DropdownMenu(expanded = menu && enabled, onDismissRequest = { menu = false; inserting = false }) {
            if (tail) {
                InsertItems { kind -> menu = false; onInsert(kind) }
            } else if (inserting) {
                DropdownMenuItem(text = { Text("‹ 返回") }, onClick = { inserting = false })
                InsertItems { kind -> menu = false; inserting = false; onInsert(kind) }
            } else {
                if (!fixed) DropdownMenuItem(text = { Text("插入上一个") }, onClick = { inserting = true })
                if (canMoveUp) DropdownMenuItem(text = { Text("上移") }, onClick = { menu = false; onMoveUp() })
                if (canMoveDown) DropdownMenuItem(text = { Text("下移") }, onClick = { menu = false; onMoveDown() })
                if (!fixed) DropdownMenuItem(text = { Text("删除节点") }, onClick = { menu = false; onDelete() })
                if (fixed) DropdownMenuItem(text = { Text("固定节点") }, enabled = false, onClick = {})
            }
        }
    }
}

@Composable
private fun InsertItems(onInsert: (String) -> Unit) {
    listOf("introduction", "chapter", "body").forEach { kind ->
        DropdownMenuItem(text = { Text(nodeTypeLabel(kind)) }, onClick = { onInsert(kind) })
    }
}

@Composable
private fun NodeCard(
    content: NodeContent,
    repository: DocumentRepository,
    colors: StatusColors,
    preview: String,
    enabled: Boolean,
    dragging: Boolean,
    highlighted: Boolean,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alpha = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var visibilityJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(enabled, dragging) {
        if (!enabled || dragging) { visibilityJob?.cancel(); alpha.snapTo(0f) }
    }
    DisposableEffect(Unit) { onDispose { visibilityJob?.cancel() } }
    val edit = {
        visibilityJob?.cancel()
        scope.launch { alpha.snapTo(0f) }
        onEdit()
    }
    val showSettings = {
        visibilityJob?.cancel()
        visibilityJob = scope.launch {
            alpha.animateTo(1f, tween(500))
            delay(3000.milliseconds)
            alpha.animateTo(0f, tween(500))
        }
    }
    val image = content.images.firstOrNull()
    val multiImage = content.images.size > 1
    val fadingVisible by remember(alpha) { derivedStateOf { alpha.value > 0f } }
    val settingsVisible = fadingVisible && enabled && !dragging
    val shape = RoundedCornerShape(12.dp)
    Box(modifier.then(if (image == null) Modifier.height(72.dp) else Modifier.aspectRatio(4f / 3f))
        .semantics {
            customActions = if (enabled) listOf(CustomAccessibilityAction("编辑节点") { edit(); true }) else emptyList()
        }
        .border(if (highlighted) 2.dp else 0.dp, if (highlighted) MaterialTheme.colorScheme.primary else Color.Transparent, shape)) {
        if (multiImage) {
            Box(Modifier.matchParentSize().padding(start = 8.dp, top = 8.dp).background(colors.foreground.copy(alpha = 0.3f), shape))
            Box(Modifier.matchParentSize().padding(start = 4.dp, top = 4.dp, end = 4.dp, bottom = 4.dp).background(colors.foreground.copy(alpha = 0.15f), shape))
        }
        Box(Modifier.fillMaxSize().padding(end = if (multiImage) 8.dp else 0.dp, bottom = if (multiImage) 8.dp else 0.dp)
            .clip(shape).background(colors.background).then(if (image != null) Modifier.padding(4.dp).clip(RoundedCornerShape(8.dp)) else Modifier)
            .clickable(enabled = enabled && !dragging, onClickLabel = "显示节点设置", onClick = showSettings)) {
            if (image == null) {
                val text = when {
                    content.node.kind == "title" || content.node.kind == "chapter" -> content.node.title
                    content.node.kind == "introduction" && content.node.bodyMarkdown.isBlank() -> "添加引言"
                    else -> preview.ifBlank {
                        if (content.node.kind == "introduction") "引言内容" else "正文内容"
                    }
                }
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text, Modifier.weight(1f).padding(start = if (content.node.kind == "introduction") 38.dp else 14.dp, end = 8.dp),
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        style = when (content.node.kind) {
                            "title" -> ComposeTypography.Font24B
                            "chapter" -> ComposeTypography.Font20M
                            "introduction" -> ComposeTypography.Font16
                            else -> ComposeTypography.Font18
                        },
                        color = if (content.node.kind == "introduction") LocalThemePalette.current.introductionContent.foreground else MaterialTheme.colorScheme.onSurface)
                    NodeEditControls(
                        type = nodeTypeLabel(content.node.kind), colors = colors, visible = settingsVisible,
                        alphaProvider = { alpha.value }, onEdit = edit, modifier = Modifier.align(Alignment.Top),
                    )
                }
            } else {
                LocalImage(repository.imageFile(content.node.documentId, image.fileName), "图片 1，共 ${content.images.size} 张", Modifier.fillMaxSize(), crop = true)
                if (content.node.bodyMarkdown.isNotBlank()) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(min = 48.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)))).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
                    Text(preview, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Color.White, style = ComposeTypography.Font16)
                }
                Row(Modifier.align(Alignment.TopCenter).fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Text("${content.images.size}", Modifier.padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp),
                        color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    NodeEditControls(
                        type = nodeTypeLabel(content.node.kind), colors = colors, visible = settingsVisible,
                        alphaProvider = { alpha.value }, onEdit = edit,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun NodeEditControls(
    type: String,
    colors: StatusColors,
    visible: Boolean,
    alphaProvider: () -> Float,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewConfiguration = LocalViewConfiguration.current
    val editViewConfiguration = remember(viewConfiguration) {
        object : ViewConfiguration by viewConfiguration {
            override val minimumTouchTargetSize = DpSize(32.dp, 32.dp)
        }
    }
    Row(modifier.graphicsLayer { alpha = if (visible) alphaProvider() else 0f }
        .then(if (visible) Modifier else Modifier.clearAndSetSemantics { }).padding(4.dp),
        horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        Text(type, Modifier.background(colors.background, RoundedCornerShape(6.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
            style = ComposeTypography.Font12sp, color = colors.foreground,
            maxLines = 1, softWrap = false)
        Spacer(Modifier.width(4.dp))
        CompositionLocalProvider(LocalViewConfiguration provides editViewConfiguration) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                .clickable(enabled = visible, role = Role.Button, onClick = onEdit).semantics { contentDescription = "编辑节点" },
                contentAlignment = Alignment.Center) {
                Box(Modifier.size(24.dp).background(colors.background, RoundedCornerShape(6.dp))
                    .border(1.dp, colors.foreground, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.foreground)
                }
            }
        }
    }
}
