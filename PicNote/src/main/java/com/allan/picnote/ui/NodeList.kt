package com.allan.picnote.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.allan.picnote.R
import com.allan.picnote.data.entity.DocumentContent
import com.allan.picnote.data.entity.NodeContent
import com.allan.picnote.data.NodeGuideKind
import com.allan.picnote.data.repositories.DocumentRepository
import com.allan.picnote.ui.theme.LocalThemePalette
import com.allan.picnote.ui.theme.StatusColors
import com.au.module_androiduiex.styles.ComposeTypography
import com.au.module_androiduiex.styles.onClick
import com.au.module_androiduiex.styles.ComposeTextButton as TextButton
import com.au.module_androiduiex.styles.ComposeIconButton as IconButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.hypot
import kotlin.time.Duration.Companion.milliseconds

@Composable
private fun nodeTypeLabel(kind: String): String = stringResource(when (kind) {
    "title" -> R.string.title
    "introduction" -> R.string.introduction
    "chapter" -> R.string.chapter_title
    else -> R.string.body
})

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
    onMoveImage: (String, String, Int) -> Unit,
    onNodeMenuOpened: () -> Unit,
    onNodeCardClicked: () -> Unit,
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = 24.dp,
    guideNodeId: String? = null,
    guideKind: NodeGuideKind? = null,
    guideAnchor: NodeGuideAnchor? = null,
) {
    var deleting by remember(content.document.id) { mutableStateOf<NodeContent?>(null) }
    var merging by remember(content.document.id) { mutableStateOf<Pair<String, String>?>(null) }
    var dragging by remember(content.document.id) { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    var targetId by remember { mutableStateOf<String?>(null) }
    val cardCoordinates = remember(content.document.id) { mutableMapOf<String, LayoutCoordinates>() }
    val previews = rememberNodePreviews(content.nodes)
    val chapterNumbers = remember(content.nodes) {
        buildMap {
            var number = 0
            content.nodes.forEach { item ->
                if (item.node.kind == "chapter") {
                    number += 1
                    put(item.node.id, number.toString().padStart(2, '0'))
                }
            }
        }
    }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val interactive = enabled && active && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(interactive) {
        if (!interactive) { dragging = null; targetId = null; dragOffset = Offset.Zero }
    }
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val palette = LocalThemePalette.current
    LazyColumn(state = listState, modifier = modifier.fillMaxSize()
        .then(if (guideKind == NodeGuideKind.Card && guideAnchor != null) Modifier.onLayoutRectChanged(debounceMillis = 0) {
            guideAnchor.updateViewport(it.boundsInWindow)
        } else Modifier), contentPadding = PaddingValues(start = 4.dp, end = 16.dp, top = 16.dp, bottom = bottomContentPadding)) {
        itemsIndexed(content.nodes, key = { _, item -> item.node.id }, contentType = { _, item ->
            when {
                item.node.kind == "chapter" -> "chapter"
                item.images.isEmpty() -> "text"
                else -> "picture"
            }
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
            val sectionLabel = when (item.node.kind) {
                "title", "introduction" -> nodeTypeLabel(item.node.kind)
                "chapter" -> chapterNumbers[item.node.id]?.let { stringResource(R.string.chapter_number, it) }
                else -> null
            }
            sectionLabel?.let { label ->
                SectionDivider(label, Modifier.padding(start = 48.dp, top = 6.dp, bottom = 12.dp))
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
                    onOpen = onNodeMenuOpened,
                    guideAnchor = if (guideKind == NodeGuideKind.Menu && item.node.id == guideNodeId) guideAnchor else null,
                )
                NodeCard(item, repository, colors, preview = previews[item.node.bodyMarkdown].orEmpty(),
                    enabled = interactive, dragging = dragging == item.node.id,
                    highlighted = targetId == item.node.id, onEdit = { onEdit(item) },
                    onMoveImage = { imageId, direction -> onMoveImage(item.node.id, imageId, direction) },
                    onCardClick = onNodeCardClicked,
                    guideAnchor = if (guideKind == NodeGuideKind.Card) guideAnchor else null,
                    guideIndex = index,
                    modifier = Modifier.weight(1f).graphicsLayer {
                        translationX = if (dragging == item.node.id) dragOffset.x else 0f
                        translationY = if (dragging == item.node.id) dragOffset.y else 0f
                    }.then(if (item.node.kind == "body") Modifier.onPlaced { cardCoordinates[item.node.id] = it } else Modifier)
                        .then(dragModifier))
            }
        }
        item(key = "insert-tail", contentType = "insert-tail") {
            Row(Modifier.fillMaxWidth().padding(start = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                var menu by remember { mutableStateOf(false) }
                LaunchedEffect(interactive) { if (!interactive) menu = false }
                Box(Modifier.clickable(enabled = interactive, role = Role.Button) { menu = true }
                    .padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.add_node), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    DropdownMenu(expanded = menu && interactive, onDismissRequest = { menu = false }) {
                        InsertItems { kind -> menu = false; onInsert(null, kind) }
                    }
                }
            }
        }
    }
    if (active) deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.delete_node)) },
            text = { Text(if (item.node.kind == "chapter") stringResource(R.string.delete_chapter_message, item.node.title) else stringResource(R.string.delete_node_message, item.images.size)) },
            confirmButton = { TextButton(onClick = { onDelete(item.node.id); deleting = null }, enabled = interactive) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (active) merging?.let { pair ->
        val selected = content.nodes.withIndex().filter { it.value.node.id == pair.first || it.value.node.id == pair.second }
        val valid = selected.size == 2 && selected.all { it.value.node.kind == "body" }
        AlertDialog(
            onDismissRequest = { merging = null }, title = { Text(stringResource(R.string.merge_nodes)) },
            text = { Text(if (valid) {
                val earlier = selected[0]
                val later = selected[1]
                val firstNumber = (earlier.index + 1).toString().padStart(2, '0')
                val secondNumber = (later.index + 1).toString().padStart(2, '0')
                stringResource(R.string.merge_nodes_message, firstNumber, earlier.value.images.size, secondNumber, later.value.images.size)
            } else stringResource(R.string.merge_nodes_changed)) },
            confirmButton = { TextButton(onClick = { merging = null; onMerge(pair.first, pair.second) }, enabled = valid && interactive) { Text(stringResource(R.string.merge)) } },
            dismissButton = { TextButton(onClick = { merging = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun NodeMenu(
    number: String,
    enabled: Boolean,
    foreground: Color,
    fixed: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onInsert: (String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    guideAnchor: NodeGuideAnchor? = null,
) {
    var menu by remember { mutableStateOf(false) }
    var inserting by remember { mutableStateOf(false) }
    val nodeActionsDescription = stringResource(R.string.node_actions)
    LaunchedEffect(enabled) { if (!enabled) { menu = false; inserting = false } }
    if (guideAnchor != null) DisposableEffect(guideAnchor) { onDispose { guideAnchor.bounds = null } }
    Box(modifier.size(width = 48.dp, height = 68.dp), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.matchParentSize()
            .onClick(enabled = enabled, onClickLabel = nodeActionsDescription, role = Role.Button) {
                inserting = false
                menu = true
                onOpen()
            }.semantics { contentDescription = nodeActionsDescription }, contentAlignment = Alignment.TopCenter) {
            Column(Modifier.background(MaterialTheme.colorScheme.background),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(R.drawable.node_bamboo_long_branch),
                    contentDescription = null,
                    modifier = Modifier.size(width = 24.dp, height = 39.dp),
                )
                Spacer(Modifier.height(1.dp))
                Text(number, Modifier.padding(horizontal = 2.dp)
                    .then(if (guideAnchor != null) Modifier.onLayoutRectChanged(debounceMillis = 0) { guideAnchor.bounds = it.boundsInWindow } else Modifier),
                    style = MaterialTheme.typography.labelSmall, color = foreground, maxLines = 1, softWrap = false)
            }
        }
        DropdownMenu(expanded = menu && enabled, onDismissRequest = { menu = false; inserting = false }) {
            if (inserting) {
                DropdownMenuItem(text = { Text("‹ ${stringResource(R.string.back)}") }, onClick = { inserting = false })
                InsertItems { kind -> menu = false; inserting = false; onInsert(kind) }
            } else {
                if (!fixed) DropdownMenuItem(text = { Text(stringResource(R.string.insert_before)) }, onClick = { inserting = true })
                if (canMoveUp) DropdownMenuItem(text = { Text(stringResource(R.string.move_up)) }, onClick = { menu = false; onMoveUp() })
                if (canMoveDown) DropdownMenuItem(text = { Text(stringResource(R.string.move_down)) }, onClick = { menu = false; onMoveDown() })
                if (!fixed) DropdownMenuItem(text = { Text(stringResource(R.string.delete_node)) }, onClick = { menu = false; onDelete() })
                if (fixed) DropdownMenuItem(text = { Text(stringResource(R.string.fixed_node)) }, enabled = false, onClick = {})
            }
        }
    }
}

@Composable
private fun InsertItems(onInsert: (String) -> Unit) {
    listOf("chapter", "body").forEach { kind ->
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
    onMoveImage: (String, Int) -> Unit,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier,
    guideAnchor: NodeGuideAnchor? = null,
    guideIndex: Int = -1,
) {
    val controlsAlpha = remember { Animatable(0f) }
    val ripple = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var visibilityJob by remember { mutableStateOf<Job?>(null) }
    var switchingJob by remember { mutableStateOf<Job?>(null) }
    var switching by remember { mutableStateOf(false) }
    var rippleDirection by remember { mutableIntStateOf(1) }
    LaunchedEffect(enabled, dragging) {
        if (!enabled || dragging) {
            visibilityJob?.cancel()
            switchingJob?.cancel()
            controlsAlpha.snapTo(0f)
            ripple.snapTo(1f)
        }
    }
    DisposableEffect(Unit) { onDispose { visibilityJob?.cancel(); switchingJob?.cancel() } }
    if (guideAnchor != null) DisposableEffect(guideAnchor, guideIndex) { onDispose { guideAnchor.removeCard(guideIndex) } }
    val edit = {
        visibilityJob?.cancel()
        switchingJob?.cancel()
        scope.launch { controlsAlpha.snapTo(0f); ripple.snapTo(1f) }
        onEdit()
    }
    val showSettings = {
        visibilityJob?.cancel()
        visibilityJob = scope.launch {
            controlsAlpha.animateTo(1f, tween(500))
            delay(3000.milliseconds)
            controlsAlpha.animateTo(0f, tween(500))
        }
    }
    var selectedIndex by rememberSaveable(content.node.id) { mutableIntStateOf(0) }
    val imageIndex = if (selectedIndex in content.images.indices) selectedIndex else 0
    val image = content.images.getOrNull(imageIndex)
    val multiImage = content.images.size > 1
    val fadingVisible by remember(controlsAlpha) { derivedStateOf { controlsAlpha.value > 0f } }
    val settingsVisible = fadingVisible && enabled && !dragging
    val moveImage: (Int) -> Unit = { direction ->
        if (image != null && enabled && !dragging && !switching && content.images.getOrNull(imageIndex + direction) != null) {
            switching = true
            rippleDirection = direction
            switchingJob = scope.launch {
                try {
                    ripple.snapTo(0f)
                    ripple.animateTo(1f, tween(240))
                    onMoveImage(image.id, direction)
                } finally {
                    switching = false
                }
            }
        }
    }
    val shape = RoundedCornerShape(12.dp)
    Column(modifier.semantics {
        customActions = if (enabled && !dragging && !switching) buildList {
            add(CustomAccessibilityAction("编辑节点") { edit(); true })
            if (multiImage) {
                add(CustomAccessibilityAction("查看上一张图片") {
                    selectedIndex = if (imageIndex == 0) content.images.lastIndex else imageIndex - 1
                    showSettings()
                    true
                })
                add(CustomAccessibilityAction("查看下一张图片") {
                    selectedIndex = (imageIndex + 1) % content.images.size
                    showSettings()
                    true
                })
            }
        } else emptyList()
    }) {
        Box(Modifier.fillMaxWidth().then(if (image == null) Modifier.height(72.dp) else Modifier.aspectRatio(4f / 3f))
            .then(if (guideAnchor != null) Modifier.onLayoutRectChanged(debounceMillis = 0) {
                guideAnchor.updateCard(guideIndex, it.boundsInWindow, hasImages = content.images.isNotEmpty())
            } else Modifier)
            .border(if (highlighted) 2.dp else 0.dp, if (highlighted) MaterialTheme.colorScheme.primary else Color.Transparent, shape)) {
            if (multiImage) {
                Box(Modifier.matchParentSize().padding(start = 8.dp, top = 8.dp).background(colors.foreground.copy(alpha = 0.3f), shape))
                Box(Modifier.matchParentSize().padding(start = 4.dp, top = 4.dp, end = 4.dp, bottom = 4.dp).background(colors.foreground.copy(alpha = 0.15f), shape))
            }
            Box(Modifier.fillMaxSize().padding(end = if (multiImage) 8.dp else 0.dp, bottom = if (multiImage) 8.dp else 0.dp)
                .clip(shape).background(colors.background).then(if (image != null) Modifier.padding(4.dp).clip(RoundedCornerShape(8.dp)) else Modifier)
                .onClick(enabled = enabled && !dragging, onClickLabel = "显示节点设置") { showSettings(); onCardClick() }) {
                if (image == null) {
                    val text = when {
                        content.node.kind == "title" || content.node.kind == "chapter" -> content.node.title
                        content.node.kind == "introduction" && content.node.bodyMarkdown.isBlank() -> "添加引言"
                        else -> preview.ifBlank {
                            if (content.node.kind == "introduction") "引言内容" else "正文内容"
                        }
                    }
                    if (content.node.kind == "title") Layout(
                        modifier = Modifier.fillMaxSize(),
                        content = {
                            Text(text, textAlign = TextAlign.Center,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                                style = ComposeTypography.Font24B, color = MaterialTheme.colorScheme.onSurface)
                            NodeEditControls(
                                type = nodeTypeLabel(content.node.kind), colors = colors, visible = settingsVisible,
                                alphaProvider = { controlsAlpha.value }, onEdit = edit,
                            )
                        },
                    ) { measurables, constraints ->
                        val controls = measurables[1].measure(constraints.copy(minWidth = 0, minHeight = 0))
                        val startPadding = 15.dp.roundToPx()
                        val endPadding = 24.dp.roundToPx()
                        val availableWidth = constraints.maxWidth - startPadding - endPadding
                        val textWidth = if (availableWidth > 0) availableWidth else 0
                        val title = measurables[0].measure(constraints.copy(minWidth = textWidth, maxWidth = textWidth, minHeight = 0))
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            title.placeRelative(startPadding, (constraints.maxHeight - title.height) / 2)
                            controls.placeRelative(constraints.maxWidth - controls.width, 0)
                        }
                    } else Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(text, Modifier.weight(1f).padding(start = if (content.node.kind == "introduction") 38.dp else 14.dp, end = 8.dp),
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = when (content.node.kind) {
                                "chapter" -> ComposeTypography.Font20M
                                "introduction" -> ComposeTypography.Font16
                                else -> ComposeTypography.Font18
                            },
                            color = if (content.node.kind == "introduction") LocalThemePalette.current.introductionContent.foreground else MaterialTheme.colorScheme.onSurface)
                        NodeEditControls(
                            type = nodeTypeLabel(content.node.kind), colors = colors, visible = settingsVisible,
                            alphaProvider = { controlsAlpha.value }, onEdit = edit, modifier = Modifier.align(Alignment.Top),
                        )
                    }
                } else {
                    Box(Modifier.fillMaxSize().then(if (multiImage) Modifier.pointerInput(enabled, dragging, switching, content.images, imageIndex) {
                        if (!enabled || dragging || switching) return@pointerInput
                        var distance = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { distance = 0f },
                            onHorizontalDrag = { _, amount -> distance += amount },
                            onDragEnd = {
                                val threshold = 48.dp.toPx()
                                val direction = when {
                                    distance < -threshold -> 1
                                    distance > threshold -> -1
                                    else -> 0
                                }
                                if (direction != 0) {
                                    selectedIndex = (imageIndex + direction + content.images.size) % content.images.size
                                    showSettings()
                                }
                            },
                        )
                    } else Modifier).drawWithContent {
                        drawContent()
                        val progress = ripple.value
                        if (progress < 1f) {
                            val inset = 24.dp.toPx()
                            val center = Offset(if (rippleDirection < 0) inset else size.width - inset, size.height - inset)
                            val radius = hypot(size.width, size.height) * progress
                            drawCircle(Color.White.copy(alpha = 0.2f * (1f - progress)), radius, center)
                            drawCircle(Color.White.copy(alpha = 0.4f * (1f - progress)), radius, center, style = Stroke(2.dp.toPx()))
                        }
                    }) {
                        NodeStackImage(repository.imageFile(content.node.documentId, image.fileName), "图片 ${imageIndex + 1}，共 ${content.images.size} 张", Modifier.fillMaxSize())
                    }
                    val hasText = content.node.bodyMarkdown.isNotBlank()
                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (multiImage) Row(Modifier.fillMaxWidth()
                            .graphicsLayer { alpha = if (settingsVisible) controlsAlpha.value else 0f }
                            .then(if (settingsVisible) Modifier else Modifier.clearAndSetSemantics { }),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            if (imageIndex > 0) IconButton(onClick = { moveImage(-1) }, enabled = settingsVisible && !switching,
                                modifier = Modifier.size(42.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "图片前移一位", tint = Color.White,
                                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape).padding(3.dp).size(18.dp))
                            } else Spacer(Modifier.size(42.dp))
                            if (imageIndex < content.images.lastIndex) IconButton(onClick = { moveImage(1) }, enabled = settingsVisible && !switching,
                                modifier = Modifier.size(42.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, "图片后移一位", tint = Color.White,
                                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape).padding(3.dp).size(18.dp))
                            } else Spacer(Modifier.size(42.dp))
                        }
                        if (hasText) Box(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))))
                            .padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
                            Text(preview, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Color.White, style = ComposeTypography.Font16)
                        }
                    }
                    NodeEditControls(
                        type = nodeTypeLabel(content.node.kind), colors = colors, visible = settingsVisible,
                        alphaProvider = { controlsAlpha.value }, onEdit = edit,
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                }
            }
        }
        if (multiImage) {
            val indicators = rememberLazyListState()
            val indicatorColor = MaterialTheme.colorScheme.onSurface
            LaunchedEffect(imageIndex, content.images.size) { indicators.animateScrollToItem(imageIndex) }
            LazyRow(state = indicators, userScrollEnabled = false,
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 6.dp)
                    .clearAndSetSemantics { contentDescription = "图片 ${imageIndex + 1}，共 ${content.images.size} 张" },
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) {
                itemsIndexed(content.images, key = { _, value -> value.id }, contentType = { _, _ -> "indicator" }) { index, _ ->
                    Box(Modifier.size(6.dp).background(if (index == imageIndex) indicatorColor else indicatorColor.copy(alpha = 0.3f), CircleShape))
                }
            }
        }
    }
}

@Composable
private fun NodeStackImage(file: File, description: String, modifier: Modifier = Modifier) {
    var displayedFile by remember { mutableStateOf(file) }
    val imageAlpha = remember(file, displayedFile) { Animatable(if (displayedFile == file) 1f else 0f) }
    var ready by remember(file) { mutableStateOf(false) }
    LaunchedEffect(file, ready) {
        if (ready) {
            imageAlpha.animateTo(1f, tween(240))
            displayedFile = file
        }
    }
    Box(modifier) {
        if (displayedFile != file) LocalImage(displayedFile, description, Modifier.fillMaxSize(), crop = true)
        LocalImage(file, description, Modifier.fillMaxSize().graphicsLayer { alpha = imageAlpha.value }, crop = true,
            onResult = { ready = true })
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
    val editDescription = stringResource(R.string.edit_node)
    CompositionLocalProvider(LocalViewConfiguration provides editViewConfiguration) {
        Row(modifier.graphicsLayer { alpha = if (visible) alphaProvider() else 0f }
            .then(if (visible) Modifier.clickable(role = Role.Button, onClick = onEdit)
                .semantics { contentDescription = editDescription } else Modifier.clearAndSetSemantics { }).padding(4.dp),
            horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text(type, Modifier.background(colors.background, RoundedCornerShape(6.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
                style = ComposeTypography.Font12sp, color = colors.foreground,
                maxLines = 1, softWrap = false)
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Box(Modifier.size(24.dp).background(colors.background, RoundedCornerShape(6.dp))
                    .border(1.dp, colors.foreground, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.foreground)
                }
            }
        }
    }
}
