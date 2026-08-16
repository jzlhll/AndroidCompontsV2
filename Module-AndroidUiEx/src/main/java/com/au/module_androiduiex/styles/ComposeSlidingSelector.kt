package com.au.module_androiduiex.styles

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds

private val SlidingScrollThreshold = 80.dp
private val SlidingScrollStep = 3.dp
private val SlidingModeDecisionDuration = 200.milliseconds
private const val NoPosition = -1

/** Compose Lazy 列表滑选的数据状态。 */
class ComposeSlidingSelectorState internal constructor(
    private val enabledProvider: () -> Boolean,
    private val itemPositionProvider: (Any) -> Int?,
    private val itemSelectableProvider: (Int) -> Boolean,
    private val itemSelectedProvider: (Int) -> Boolean,
    private val itemSelectionChanged: (Int, Boolean) -> (() -> Unit)?,
    private val itemLongPressProvider: () -> ((Int) -> Unit)?,
) {
    private val selectionRestores = mutableMapOf<Int, () -> Unit>()
    private var firstPosition = NoPosition
    private var lastPosition = NoPosition
    private var gestureSelectedState: Boolean? = null

    internal val isEnabled: Boolean
        get() = enabledProvider()

    internal val supportsLongPress: Boolean
        get() = itemLongPressProvider() != null

    internal fun itemPosition(key: Any): Int? = itemPositionProvider(key)

    internal fun dispatchLongPress(position: Int) {
        itemLongPressProvider()?.invoke(position)
    }

    internal fun beginSelection(position: Int): Boolean {
        resetSelectionRange()
        if (!itemSelectableProvider(position)) return false
        val selected = !itemSelectedProvider(position)
        val restore = itemSelectionChanged(position, selected) ?: return false
        firstPosition = position
        lastPosition = position
        gestureSelectedState = selected
        selectionRestores[position] = restore
        return true
    }

    internal fun moveSelection(position: Int) {
        if (!itemSelectableProvider(position)) return
        if (firstPosition == NoPosition || position == lastPosition) return
        val selected = gestureSelectedState ?: return
        val step = if (position > lastPosition) 1 else -1
        var currentPosition = lastPosition
        while (currentPosition != position) {
            val nextPosition = currentPosition + step
            if (abs(nextPosition - firstPosition) < abs(currentPosition - firstPosition)) {
                restoreSelection(currentPosition)
            } else {
                updateSelection(nextPosition, selected)
            }
            currentPosition = nextPosition
        }
        lastPosition = position
    }

    internal fun endSelection() {
        resetSelectionRange()
    }

    private fun updateSelection(position: Int, selected: Boolean) {
        if (!itemSelectableProvider(position) || position in selectionRestores) return
        if (itemSelectedProvider(position) == selected) return
        val restore = itemSelectionChanged(position, selected) ?: return
        selectionRestores[position] = restore
    }

    private fun restoreSelection(position: Int) {
        selectionRestores.remove(position)?.invoke()
    }

    private fun resetSelectionRange() {
        firstPosition = NoPosition
        lastPosition = NoPosition
        gestureSelectedState = null
        selectionRestores.clear()
    }
}

/**
 * 创建 Compose Lazy 列表滑选状态。
 * [onItemLongPress] 为空时不接管手势。
 * [onItemSelectionChanged] 成功时返回回缩该项目所需的撤销操作，变更失败时返回 null。
 */
@Composable
fun rememberComposeSlidingSelectorState(
    dataKey: Any?,
    itemPosition: (Any) -> Int?,
    isItemSelectable: (Int) -> Boolean,
    isItemSelected: (Int) -> Boolean,
    onItemSelectionChanged: (Int, Boolean) -> (() -> Unit)?,
    onItemLongPress: ((Int) -> Unit)?,
    enabled: Boolean = onItemLongPress != null,
): ComposeSlidingSelectorState {
    val currentEnabled = rememberUpdatedState(enabled)
    val currentItemPosition = rememberUpdatedState(itemPosition)
    val currentItemSelectable = rememberUpdatedState(isItemSelectable)
    val currentItemSelected = rememberUpdatedState(isItemSelected)
    val currentItemSelectionChanged = rememberUpdatedState(onItemSelectionChanged)
    val currentItemLongPress = rememberUpdatedState(onItemLongPress)
    return remember(dataKey) {
        ComposeSlidingSelectorState(
            enabledProvider = { currentEnabled.value },
            itemPositionProvider = { currentItemPosition.value(it) },
            itemSelectableProvider = { currentItemSelectable.value(it) },
            itemSelectedProvider = { currentItemSelected.value(it) },
            itemSelectionChanged = { position, selected ->
                currentItemSelectionChanged.value(position, selected)
            },
            itemLongPressProvider = { currentItemLongPress.value },
        )
    }
}

/** 为垂直固定网格列表增加长按预览与滑选手势。 */
@Composable
fun Modifier.composeSlidingSelector(
    selectorState: ComposeSlidingSelectorState,
    lazyGridState: LazyGridState,
): Modifier {
    val hapticFeedback = LocalHapticFeedback.current
    val currentHapticFeedback = rememberUpdatedState(hapticFeedback)
    return slidingSelectorPointerInput(
        selectorState = selectorState,
        scrollOwner = lazyGridState,
        itemKeyAt = { point -> lazyGridState.itemKeyAt(point) },
        scrollBy = { delta -> lazyGridState.scrollBy(delta) },
        onLongPressReady = {
            currentHapticFeedback.value.performHapticFeedback(HapticFeedbackType.LongPress)
        },
    )
}

/** 为垂直瀑布流列表增加长按预览与滑选手势。 */
@Composable
fun Modifier.composeSlidingSelector(
    selectorState: ComposeSlidingSelectorState,
    lazyStaggeredGridState: LazyStaggeredGridState,
): Modifier {
    val hapticFeedback = LocalHapticFeedback.current
    val currentHapticFeedback = rememberUpdatedState(hapticFeedback)
    return slidingSelectorPointerInput(
        selectorState = selectorState,
        scrollOwner = lazyStaggeredGridState,
        itemKeyAt = { point -> lazyStaggeredGridState.itemKeyAt(point) },
        scrollBy = { delta -> lazyStaggeredGridState.scrollBy(delta) },
        onLongPressReady = {
            currentHapticFeedback.value.performHapticFeedback(HapticFeedbackType.LongPress)
        },
    )
}

private fun Modifier.slidingSelectorPointerInput(
    selectorState: ComposeSlidingSelectorState,
    scrollOwner: Any,
    itemKeyAt: (Offset) -> Any?,
    scrollBy: suspend (Float) -> Float,
    onLongPressReady: () -> Unit,
): Modifier {
    return pointerInput(selectorState, scrollOwner) {
        val scrollThreshold = SlidingScrollThreshold.toPx()
        val scrollStep = SlidingScrollStep.toPx()
        coroutineScope {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (!selectorState.isEnabled) return@awaitEachGesture
                val pointerId = down.id
                val startPosition = itemKeyAt(down.position)
                    ?.let(selectorState::itemPosition)
                    ?: return@awaitEachGesture

                when (val initialDecision = awaitHorizontalSlidingOrLongPress(down)) {
                    InitialGestureDecision.Canceled -> Unit
                    InitialGestureDecision.LongPress -> {
                        if (!selectorState.supportsLongPress) return@awaitEachGesture
                        onLongPressReady()
                        when (val decision = awaitSlidingModeDecision(down)) {
                            LongPressDecision.Canceled -> Unit
                            is LongPressDecision.Preview -> {
                                selectorState.dispatchLongPress(startPosition)
                                if (decision.pointerPressed) {
                                    consumeUntilUp()
                                }
                            }
                            is LongPressDecision.Sliding -> {
                                if (!selectorState.beginSelection(startPosition)) {
                                    consumeUntilUp()
                                    return@awaitEachGesture
                                }
                                var pointerPosition = decision.pointerPosition
                                moveSelectionAt(pointerPosition, itemKeyAt, selectorState)
                                val scrollJob = launch {
                                    while (isActive && selectorState.isEnabled) {
                                        withFrameNanos { }
                                        val autoScroll = calculateAutoScroll(
                                            pointerY = pointerPosition.y,
                                            viewportHeight = size.height.toFloat(),
                                            threshold = scrollThreshold,
                                            step = scrollStep,
                                        )
                                        if (autoScroll == 0f || scrollBy(autoScroll) == 0f) continue
                                        moveSelectionAt(pointerPosition, itemKeyAt, selectorState)
                                    }
                                }
                                try {
                                    while (selectorState.isEnabled) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        event.changes.forEach { it.consume() }
                                        val change = event.changes.firstOrNull { it.id == pointerId }
                                        if (change == null || !change.pressed ||
                                            event.changes.any { it.id != pointerId && it.pressed }
                                        ) {
                                            break
                                        }
                                        pointerPosition = change.position
                                        moveSelectionAt(pointerPosition, itemKeyAt, selectorState)
                                    }
                                } finally {
                                    scrollJob.cancel()
                                    selectorState.endSelection()
                                }
                            }
                        }
                    }
                    is InitialGestureDecision.Sliding -> {
                        if (!selectorState.beginSelection(startPosition)) {
                            consumeUntilUp()
                            return@awaitEachGesture
                        }
                        var pointerPosition = initialDecision.pointerPosition
                        moveSelectionAt(pointerPosition, itemKeyAt, selectorState)
                        val scrollJob = launch {
                            while (isActive && selectorState.isEnabled) {
                                withFrameNanos { }
                                val autoScroll = calculateAutoScroll(
                                    pointerY = pointerPosition.y,
                                    viewportHeight = size.height.toFloat(),
                                    threshold = scrollThreshold,
                                    step = scrollStep,
                                )
                                if (autoScroll == 0f || scrollBy(autoScroll) == 0f) continue
                                moveSelectionAt(pointerPosition, itemKeyAt, selectorState)
                            }
                        }
                        try {
                            while (selectorState.isEnabled) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                event.changes.forEach { it.consume() }
                                val change = event.changes.firstOrNull { it.id == pointerId }
                                if (change == null || !change.pressed ||
                                    event.changes.any { it.id != pointerId && it.pressed }
                                ) {
                                    break
                                }
                                pointerPosition = change.position
                                moveSelectionAt(pointerPosition, itemKeyAt, selectorState)
                            }
                        } finally {
                            scrollJob.cancel()
                            selectorState.endSelection()
                        }
                    }
                }
            }
        }
    }
}

private suspend fun AwaitPointerEventScope.awaitHorizontalSlidingOrLongPress(
    down: PointerInputChange,
): InitialGestureDecision {
    return withTimeoutOrNull<InitialGestureDecision>(viewConfiguration.longPressTimeoutMillis) {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id }
            when {
                change == null || !change.pressed ||
                    event.changes.any { it.id != down.id && it.pressed } -> {
                    return@withTimeoutOrNull InitialGestureDecision.Canceled
                }
                else -> {
                    val offset = change.position - down.position
                    val horizontalDistance = abs(offset.x)
                    val verticalDistance = abs(offset.y)
                    if (horizontalDistance > viewConfiguration.touchSlop &&
                        horizontalDistance > verticalDistance
                    ) {
                        return@withTimeoutOrNull InitialGestureDecision.Sliding(change.position)
                    }
                    if (verticalDistance > viewConfiguration.touchSlop) {
                        return@withTimeoutOrNull InitialGestureDecision.Canceled
                    }
                }
            }
        }
        InitialGestureDecision.Canceled
    } ?: InitialGestureDecision.LongPress
}

private sealed interface InitialGestureDecision {
    data object Canceled : InitialGestureDecision
    data object LongPress : InitialGestureDecision
    data class Sliding(val pointerPosition: Offset) : InitialGestureDecision
}

private suspend fun AwaitPointerEventScope.awaitSlidingModeDecision(
    longPressChange: PointerInputChange,
): LongPressDecision {
    val longPressPosition = longPressChange.position
    return withTimeoutOrNull<LongPressDecision>(SlidingModeDecisionDuration.inWholeMilliseconds) {
        var decision: LongPressDecision? = null
        while (decision == null) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == longPressChange.id }
            decision = when {
                change == null -> LongPressDecision.Canceled
                event.changes.any { it.id != longPressChange.id && it.pressed } -> LongPressDecision.Canceled
                !change.pressed -> LongPressDecision.Preview(pointerPressed = false)
                isHorizontalSliding(
                    offset = change.position - longPressPosition,
                    touchSlop = viewConfiguration.touchSlop,
                ) -> {
                    LongPressDecision.Sliding(change.position)
                }
                abs((change.position - longPressPosition).y) > viewConfiguration.touchSlop -> {
                    LongPressDecision.Canceled
                }
                else -> null
            }
        }
        decision ?: LongPressDecision.Canceled
    } ?: LongPressDecision.Preview(pointerPressed = true)
}

private fun isHorizontalSliding(offset: Offset, touchSlop: Float): Boolean {
    return abs(offset.x) > touchSlop && abs(offset.x) > abs(offset.y)
}

private suspend fun AwaitPointerEventScope.consumeUntilUp() {
    var hasPressedPointer: Boolean
    do {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        event.changes.forEach { it.consume() }
        hasPressedPointer = event.changes.any { it.pressed }
    } while (hasPressedPointer)
}

private fun moveSelectionAt(
    pointerPosition: Offset,
    itemKeyAt: (Offset) -> Any?,
    selectorState: ComposeSlidingSelectorState,
) {
    val position = itemKeyAt(pointerPosition)?.let(selectorState::itemPosition) ?: return
    selectorState.moveSelection(position)
}

private fun LazyGridState.itemKeyAt(point: Offset): Any? {
    val currentLayoutInfo = layoutInfo
    val contentPoint = point.toVerticalLazyContentPoint(
        viewportStartOffset = currentLayoutInfo.viewportStartOffset,
    )
    return currentLayoutInfo.visibleItemsInfo.lastOrNull { item ->
        contentPoint.x >= item.offset.x &&
            contentPoint.x < item.offset.x + item.size.width &&
            contentPoint.y >= item.offset.y &&
            contentPoint.y < item.offset.y + item.size.height
    }?.key
}

private fun LazyStaggeredGridState.itemKeyAt(point: Offset): Any? {
    val currentLayoutInfo = layoutInfo
    val contentPoint = point.toVerticalLazyContentPoint(
        viewportStartOffset = currentLayoutInfo.viewportStartOffset,
    )
    return currentLayoutInfo.visibleItemsInfo.lastOrNull { item ->
        contentPoint.x >= item.offset.x &&
            contentPoint.x < item.offset.x + item.size.width &&
            contentPoint.y >= item.offset.y &&
            contentPoint.y < item.offset.y + item.size.height
    }?.key
}

private fun Offset.toVerticalLazyContentPoint(viewportStartOffset: Int): Offset {
    return copy(y = y + viewportStartOffset)
}

private fun calculateAutoScroll(
    pointerY: Float,
    viewportHeight: Float,
    threshold: Float,
    step: Float,
): Float {
    val distanceFromEdge: Float
    val direction: Float
    when {
        pointerY > viewportHeight - threshold -> {
            distanceFromEdge = max(viewportHeight - pointerY, 0f)
            direction = 1f
        }
        pointerY < threshold -> {
            distanceFromEdge = max(pointerY, 0f)
            direction = -1f
        }
        else -> return 0f
    }
    val ratio = 1 - distanceFromEdge / threshold
    val multiplier = when {
        ratio > 0.9f -> 6f
        ratio > 0.8f -> 4f
        ratio > 0.5f -> 3f
        ratio > 0.3f -> 2f
        else -> 1f
    }
    return direction * step * multiplier
}

private sealed interface LongPressDecision {
    data object Canceled : LongPressDecision
    data class Preview(val pointerPressed: Boolean) : LongPressDecision
    data class Sliding(val pointerPosition: Offset) : LongPressDecision
}
