package com.au.module_androiduiex.dialogs

import android.graphics.Color.TRANSPARENT
import android.view.WindowManager
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsAnimationCompat
import com.au.module_androiduiex.styles.ComposeColors
import com.au.module_androiduiex.styles.ComposeDimens
import com.au.module_androiduiex.styles.ComposeTypography
import com.au.module_androiduiex.styles.noBackClickable
import kotlin.math.abs
import kotlinx.coroutines.launch

private val SheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
private val HeadLineWidth = 32.dp
private val HeadLineHeight = 4.dp
private val HeadLineTopPadding = 6.dp
private val HeadLineBottomPadding = 12.dp
private val HeadLineWithoutToolbarBottomPadding = 14.dp
private val HeadLineShape = RoundedCornerShape(2.dp)
private val HeaderHeight = 56.dp
private val HeaderBackSlotWidth = 52.dp

sealed interface ComposeBottomSheetToolbar {
    data object Default : ComposeBottomSheetToolbar

    data object None : ComposeBottomSheetToolbar

    class Custom(
        val content: @Composable (dismiss: () -> Unit) -> Unit,
    ) : ComposeBottomSheetToolbar
}

/**
 * Compose 通用底部弹窗，调用方通过条件组合控制显示。
 *
 * [height] 为 null 或 0dp 时按内容自适应高度，为 [Dp.Infinity] 时占满最大可用高度。
 * [canCancel] 控制返回键、点击外部与 Sheet 拖拽关闭。
 * [contentCanDragSheet] 为 true 时保留 Material3 整个 Sheet 响应拖拽的传统逻辑；为 false 时隔离内容区
 * 的纵向手势，仅 UiEx 横杆与 toolbar 能够拖动 Sheet。
 * [showToolbar] 控制使用内置 toolbar、不显示 toolbar 或注入自定义 toolbar。
 * [content] 通过安全区 padding 避让系统栏，内容背景仍可绘制到系统栏下方。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "",
    @DrawableRes leftIconResId: Int? = null,
    backgroundColor: Color? = null,
    canCancel: Boolean = true,
    contentCanDragSheet: Boolean = true,
    showHeadLine: Boolean = true,
    showToolbar: ComposeBottomSheetToolbar = ComposeBottomSheetToolbar.Default,
    height: Dp? = null,
    maxHeightInset: Dp = 0.dp,
    followKeyboard: Boolean = true,
    content: @Composable (dismiss: () -> Unit, safeAreaPadding: PaddingValues) -> Unit,
) {
    require(height == null || height >= 0.dp) { "height must be >= 0.dp" }
    require(maxHeightInset >= 0.dp) { "maxHeightInset must be >= 0.dp" }

    val density = LocalDensity.current
    val windowHeight = LocalWindowInfo.current.containerDpSize.height
    val statusBarHeight = with(density) {
        WindowInsets.statusBars.getTop(this).toDp()
    }
    val calculatedMaxHeight = windowHeight - statusBarHeight - maxHeightInset
    val maxSheetHeight = if (calculatedMaxHeight > 0.dp) calculatedMaxHeight else 0.dp
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    var isDismissing by remember { mutableStateOf(false) }

    val dismissSheet: () -> Unit = {
        if (!isDismissing) {
            isDismissing = true
            coroutineScope.launch {
                sheetState.hide()
                if (!sheetState.isVisible) {
                    currentOnDismissRequest()
                } else {
                    isDismissing = false
                }
            }
        }
    }
    val sheetBackgroundColor = backgroundColor ?: ComposeColors.PrimaryBg

    MaterialTheme {
        ModalBottomSheet(
            onDismissRequest = { currentOnDismissRequest() },
            sheetState = sheetState,
            sheetGesturesEnabled = canCancel,
            modifier = modifier,
            shape = SheetShape,
            containerColor = sheetBackgroundColor,
            dragHandle = null,
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
            properties = ModalBottomSheetProperties(
                shouldDismissOnBackPress = canCancel,
                shouldDismissOnClickOutside = canCancel,
            ),
        ) {
            ConfigureBottomSheetWindow(followKeyboard)
            ComposeBottomSheetScaffold(
                title = title,
                leftIconResId = leftIconResId,
                onBackClick = dismissSheet,
                showHeadLine = showHeadLine,
                showToolbar = showToolbar,
                blockContentSheetGestures = canCancel && !contentCanDragSheet,
                height = height,
                maxSheetHeight = maxSheetHeight,
                dismiss = dismissSheet,
                content = content,
            )
        }
    }
}

@Composable
private fun ConfigureBottomSheetWindow(followKeyboard: Boolean) {
    val view = LocalView.current
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        window.navigationBarColor = TRANSPARENT
        window.isNavigationBarContrastEnforced = false
    }
    DisposableEffect(view, followKeyboard) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (followKeyboard || window == null) {
            onDispose { }
        } else {
            val previousMode = window.attributes.softInputMode
            val decorView = window.decorView
            // Material 弹窗内部仍有 imePadding，过滤本弹窗的 IME inset 以保持位置固定。
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            ViewCompat.setOnApplyWindowInsetsListener(decorView) { _, insets -> insets.withoutIme() }
            ViewCompat.setWindowInsetsAnimationCallback(decorView, object : WindowInsetsAnimationCompat.Callback(
                DISPATCH_MODE_CONTINUE_ON_SUBTREE,
            ) {
                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>,
                ): WindowInsetsCompat = insets.withoutIme()
            })
            ViewCompat.requestApplyInsets(decorView)
            onDispose {
                ViewCompat.setOnApplyWindowInsetsListener(decorView, null)
                ViewCompat.setWindowInsetsAnimationCallback(decorView, null)
                window.setSoftInputMode(previousMode)
            }
        }
    }
}

private fun WindowInsetsCompat.withoutIme(): WindowInsetsCompat = WindowInsetsCompat.Builder(this)
    .setInsets(WindowInsetsCompat.Type.ime(), Insets.NONE)
    .setVisible(WindowInsetsCompat.Type.ime(), false)
    .build()

@Composable
private fun ComposeBottomSheetScaffold(
    title: String,
    @DrawableRes leftIconResId: Int?,
    onBackClick: () -> Unit,
    showHeadLine: Boolean,
    showToolbar: ComposeBottomSheetToolbar,
    blockContentSheetGestures: Boolean,
    height: Dp?,
    maxSheetHeight: Dp,
    dismiss: () -> Unit,
    content: @Composable (dismiss: () -> Unit, safeAreaPadding: PaddingValues) -> Unit,
) {
    val fixedHeight = height?.let {
        when {
            it == 0.dp -> null
            it < maxSheetHeight -> it
            else -> maxSheetHeight
        }
    }
    val sizeModifier = if (fixedHeight == null) {
        Modifier.heightIn(max = maxSheetHeight)
    } else {
        Modifier.height(fixedHeight)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(sizeModifier),
    ) {
        if (showHeadLine) {
            BottomSheetHeadLine(
                bottomPadding = if (showToolbar == ComposeBottomSheetToolbar.None) {
                    HeadLineWithoutToolbarBottomPadding
                } else {
                    HeadLineBottomPadding
                },
            )
        }
        when (showToolbar) {
            ComposeBottomSheetToolbar.Default -> {
                BottomSheetHeader(
                    title = title,
                    leftIconResId = leftIconResId,
                    onBackClick = onBackClick,
                )
            }
            ComposeBottomSheetToolbar.None -> Unit
            is ComposeBottomSheetToolbar.Custom -> showToolbar.content(dismiss)
        }

        val contentModifier = if (fixedHeight == null) {
            Modifier.fillMaxWidth()
        } else {
            Modifier
                .fillMaxWidth()
                .weight(1f)
        }
        Box(
            modifier = contentModifier.then(
                if (blockContentSheetGestures) {
                    Modifier.blockSheetGesturesFromContent()
                } else {
                    Modifier
                },
            ),
        ) {
            content(dismiss, WindowInsets.navigationBars.asPaddingValues())
        }
    }
}

private fun Modifier.blockSheetGesturesFromContent(): Modifier {
    return nestedScroll(BlockSheetContentNestedScrollConnection)
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var accumulatedPan = Offset.Zero
                var directionDecided = false
                var blockVerticalDrag = false
                var event: PointerEvent

                do {
                    event = awaitPointerEvent()
                    if (!directionDecided) {
                        accumulatedPan += event.calculatePan()
                        if (accumulatedPan.getDistance() > viewConfiguration.touchSlop) {
                            directionDecided = true
                            blockVerticalDrag = abs(accumulatedPan.y) > abs(accumulatedPan.x)
                        }
                    }
                    if (blockVerticalDrag) {
                        event.changes.forEach { change ->
                            if (change.positionChanged() && !change.isConsumed) {
                                change.consume()
                            }
                        }
                    }
                } while (event.changes.any { it.pressed })
            }
        }
}

private object BlockSheetContentNestedScrollConnection : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        return Offset(x = 0f, y = available.y)
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        return Velocity(x = 0f, y = available.y)
    }
}

@Composable
private fun BottomSheetHeadLine(bottomPadding: Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = HeadLineTopPadding, bottom = bottomPadding),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = HeadLineWidth, height = HeadLineHeight)
                .background(
                    color = ComposeColors.TextDescD9,
                    shape = HeadLineShape,
                ),
        )
    }
}

@Composable
private fun BottomSheetHeader(
    title: String,
    @DrawableRes leftIconResId: Int?,
    onBackClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HeaderHeight),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = ComposeTypography.Font20M,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HeaderBackSlotWidth),
        )

        if (leftIconResId != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(HeaderBackSlotWidth)
                    .height(HeaderHeight)
                    .noBackClickable(onClick = onBackClick),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Image(
                    painter = painterResource(leftIconResId),
                    contentDescription = null,
                    modifier = Modifier.size(ComposeDimens.ToolbarHeight),
                )
            }
        }
    }
}
