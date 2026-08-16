package com.au.module_androiduiex.styles

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** 统一管理分组列表的浮动 Header 位置与列表 Header 过渡透明度。 */
@Stable
class ComposeFloatingGroupHeaderState internal constructor() {
    var currentHeaderIndex: Int by mutableIntStateOf(0)
        internal set

    private var transitionAlpha by mutableFloatStateOf(1f)

    fun headerAlpha(headerIndex: Int): Float {
        return when {
            headerIndex <= currentHeaderIndex -> 0f
            headerIndex == currentHeaderIndex + 1 -> transitionAlpha
            else -> 1f
        }
    }

    internal fun update(headerIndex: Int, alpha: Float) {
        currentHeaderIndex = headerIndex
        transitionAlpha = alpha
    }
}

/** 为普通 Lazy 列表创建浮动分组 Header 状态。 */
@Composable
fun rememberComposeFloatingGroupHeaderState(
    lazyListState: LazyListState,
    headerItemIndices: List<Int>,
    stickyTop: Dp,
    transitionHeight: Dp,
): ComposeFloatingGroupHeaderState {
    return rememberComposeFloatingGroupHeaderState(
        sourceKey = lazyListState,
        headerItemIndices = headerItemIndices,
        stickyTop = stickyTop,
        transitionHeight = transitionHeight,
        visibleItemsProvider = {
            lazyListState.layoutInfo.visibleItemsInfo.map {
                FloatingGroupVisibleItem(index = it.index, offset = it.offset)
            }
        },
    )
}

/** 为固定网格 Lazy 列表创建浮动分组 Header 状态。 */
@Composable
fun rememberComposeFloatingGroupHeaderState(
    lazyGridState: LazyGridState,
    headerItemIndices: List<Int>,
    stickyTop: Dp,
    transitionHeight: Dp,
): ComposeFloatingGroupHeaderState {
    return rememberComposeFloatingGroupHeaderState(
        sourceKey = lazyGridState,
        headerItemIndices = headerItemIndices,
        stickyTop = stickyTop,
        transitionHeight = transitionHeight,
        visibleItemsProvider = {
            lazyGridState.layoutInfo.visibleItemsInfo.map {
                FloatingGroupVisibleItem(index = it.index, offset = it.offset.y)
            }
        },
    )
}

/** 为瀑布流 Lazy 列表创建浮动分组 Header 状态。 */
@Composable
fun rememberComposeFloatingGroupHeaderState(
    lazyStaggeredGridState: LazyStaggeredGridState,
    headerItemIndices: List<Int>,
    stickyTop: Dp,
    transitionHeight: Dp,
): ComposeFloatingGroupHeaderState {
    return rememberComposeFloatingGroupHeaderState(
        sourceKey = lazyStaggeredGridState,
        headerItemIndices = headerItemIndices,
        stickyTop = stickyTop,
        transitionHeight = transitionHeight,
        visibleItemsProvider = {
            lazyStaggeredGridState.layoutInfo.visibleItemsInfo.map {
                FloatingGroupVisibleItem(index = it.index, offset = it.offset.y)
            }
        },
    )
}

@Composable
private fun rememberComposeFloatingGroupHeaderState(
    sourceKey: Any,
    headerItemIndices: List<Int>,
    stickyTop: Dp,
    transitionHeight: Dp,
    visibleItemsProvider: () -> List<FloatingGroupVisibleItem>,
): ComposeFloatingGroupHeaderState {
    val density = LocalDensity.current
    val stickyTopPx = with(density) { stickyTop.roundToPx() }
    val stickyBottomPx = with(density) { (stickyTop + transitionHeight).roundToPx() }
    val state = remember(sourceKey, headerItemIndices) { ComposeFloatingGroupHeaderState() }

    LaunchedEffect(sourceKey, headerItemIndices, stickyTopPx, stickyBottomPx) {
        snapshotFlow {
            visibleItemsProvider().sortedBy { it.index }
        }
            .map { visibleItems ->
                computeFloatingGroupHeaderTransition(
                    visibleItems = visibleItems,
                    headerItemIndices = headerItemIndices,
                    stickyTopPx = stickyTopPx,
                    stickyBottomPx = stickyBottomPx,
                )
            }
            .distinctUntilChanged()
            .collect { transition ->
                state.update(
                    headerIndex = transition.currentHeaderIndex,
                    alpha = transition.transitionAlpha,
                )
            }
    }

    return state
}

private data class FloatingGroupVisibleItem(
    val index: Int,
    val offset: Int,
)

private data class FloatingGroupHeaderTransition(
    val currentHeaderIndex: Int,
    val transitionAlpha: Float,
)

private fun computeFloatingGroupHeaderTransition(
    visibleItems: List<FloatingGroupVisibleItem>,
    headerItemIndices: List<Int>,
    stickyTopPx: Int,
    stickyBottomPx: Int,
): FloatingGroupHeaderTransition {
    if (visibleItems.isEmpty() || headerItemIndices.isEmpty()) {
        return FloatingGroupHeaderTransition(currentHeaderIndex = 0, transitionAlpha = 1f)
    }

    var currentHeaderIndex = 0
    var transitionAlpha = 1f
    val firstVisibleItemIndex = visibleItems.first().index

    for (headerIndex in headerItemIndices.indices) {
        val headerItemIndex = headerItemIndices[headerIndex]
        if (headerItemIndex < firstVisibleItemIndex) {
            currentHeaderIndex = headerIndex
            continue
        }

        val visibleHeader = visibleItems.find { it.index == headerItemIndex } ?: break
        when {
            visibleHeader.offset <= stickyTopPx -> currentHeaderIndex = headerIndex
            visibleHeader.offset < stickyBottomPx -> {
                transitionAlpha = (visibleHeader.offset - stickyTopPx).toFloat() /
                    (stickyBottomPx - stickyTopPx).toFloat()
                break
            }
            else -> break
        }
    }

    return FloatingGroupHeaderTransition(currentHeaderIndex, transitionAlpha)
}
