package com.au.module_androiduiex.styles

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bumptech.glide.load.engine.DiskCacheStrategy
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val MinPreviewScale = 1f
private const val MaxPreviewScale = 4f
private const val EdgeTolerancePx = 0.5f

/** 单图预览的缩放与平移状态。 */
@Stable
class ComposeImagePreviewState internal constructor() {
    var imageScale by mutableFloatStateOf(MinPreviewScale)
        internal set
    var imageOffset by mutableStateOf(Offset.Zero)
        internal set

    /** 图片是否处于可下拉退出的初始状态。 */
    val isAtRest: Boolean
        get() = imageScale == MinPreviewScale && imageOffset == Offset.Zero
}

/** 创建随图片标识及比例重置的单图预览状态。 */
@Composable
fun rememberComposeImagePreviewState(
    previewKey: Any?,
    imageAspectRatio: Float,
): ComposeImagePreviewState {
    return remember(previewKey, imageAspectRatio) { ComposeImagePreviewState() }
}

/** 使用 Glide 加载的单图预览。 */
@Composable
fun ComposeImagePreview(
    model: Any?,
    imageAspectRatio: Float,
    contentDescription: String?,
    initialTopPadding: Dp,
    initialHorizontalPadding: Dp,
    initialBottomPadding: Dp,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
    gesturesEnabled: Boolean = true,
    diskCacheStrategy: DiskCacheStrategy = DiskCacheStrategy.AUTOMATIC,
) {
    ComposeImagePreviewContainer(
        previewKey = model,
        imageAspectRatio = imageAspectRatio,
        initialTopPadding = initialTopPadding,
        initialHorizontalPadding = initialHorizontalPadding,
        initialBottomPadding = initialBottomPadding,
        modifier = modifier,
        gesturesEnabled = gesturesEnabled,
    ) { imageWidth, imageHeight ->
        GlideRoundedImage(
            model = model,
            contentDescription = contentDescription,
            width = imageWidth,
            height = imageHeight,
            cornerRadius = cornerRadius,
            diskCacheStrategy = diskCacheStrategy,
            contentScale = ContentScale.Fit,
        )
    }
}

/**
 * 支持缩放和平移的单图预览容器。
 *
 * [modifier] 决定图片能够绘制的最大区域，初始 padding 决定默认适配图片的内部区域。
 * [previewKey] 变化时会重置预览状态，[imageContent] 需按传入的宽高绘制图片。
 * [fitWidth] 为 true 时图片始终按可用宽度等比展示，超出可用高度的部分允许溢出。
 * 图片到达内部区域边缘后会消费完当前拖拽；下一次从边缘向外拖拽时不消费事件，供父容器接管。
 */
@Composable
fun ComposeImagePreviewContainer(
    previewKey: Any?,
    imageAspectRatio: Float,
    initialTopPadding: Dp,
    initialHorizontalPadding: Dp,
    initialBottomPadding: Dp,
    modifier: Modifier = Modifier,
    gesturesEnabled: Boolean = true,
    fitWidth: Boolean = false,
    previewState: ComposeImagePreviewState = rememberComposeImagePreviewState(
        previewKey,
        imageAspectRatio,
    ),
    dismissGestureEnabled: Boolean = false,
    onDismissDragStart: ((Offset) -> Unit)? = null,
    onDismissDrag: ((Offset) -> Unit)? = null,
    onDismissDragEnd: ((Offset, velocityY: Float) -> Unit)? = null,
    onDismissDragCancel: (() -> Unit)? = null,
    expandGestureEnabled: Boolean = false,
    onExpandDragStart: ((Offset) -> Unit)? = null,
    onExpandDrag: ((Offset) -> Unit)? = null,
    onExpandDragEnd: ((Offset, velocityY: Float) -> Unit)? = null,
    onExpandDragCancel: (() -> Unit)? = null,
    onImageBoundsChanged: ((Rect) -> Unit)? = null,
    imageContent: @Composable (width: Dp, height: Dp) -> Unit,
) {
    var lastTapUpTime by remember(previewKey) { mutableLongStateOf(0L) }
    val currentOnDismissDragStart by rememberUpdatedState(onDismissDragStart)
    val currentOnDismissDrag by rememberUpdatedState(onDismissDrag)
    val currentOnDismissDragEnd by rememberUpdatedState(onDismissDragEnd)
    val currentOnDismissDragCancel by rememberUpdatedState(onDismissDragCancel)
    val currentOnExpandDragStart by rememberUpdatedState(onExpandDragStart)
    val currentOnExpandDrag by rememberUpdatedState(onExpandDrag)
    val currentOnExpandDragEnd by rememberUpdatedState(onExpandDragEnd)
    val currentOnExpandDragCancel by rememberUpdatedState(onExpandDragCancel)
    val currentOnImageBoundsChanged by rememberUpdatedState(onImageBoundsChanged)
    val currentDismissGestureEnabled by rememberUpdatedState(dismissGestureEnabled)
    val currentExpandGestureEnabled by rememberUpdatedState(expandGestureEnabled)
    val currentGesturesEnabled by rememberUpdatedState(gesturesEnabled)
    val currentPreviewState by rememberUpdatedState(previewState)

    LaunchedEffect(gesturesEnabled) {
        if (!gesturesEnabled) {
            previewState.imageScale = MinPreviewScale
            previewState.imageOffset = Offset.Zero
            lastTapUpTime = 0L
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds(),
    ) {
        val calculatedWidth = maxWidth - initialHorizontalPadding * 2
        val calculatedHeight = maxHeight - initialTopPadding - initialBottomPadding
        val availableWidth = if (calculatedWidth > 0.dp) calculatedWidth else 0.dp
        val availableHeight = if (calculatedHeight > 0.dp) calculatedHeight else 0.dp
        if (availableWidth == 0.dp || availableHeight == 0.dp) {
            return@BoxWithConstraints
        }

        val safeAspectRatio = if (imageAspectRatio.isFinite() && imageAspectRatio > 0f) {
            imageAspectRatio
        } else {
            1f
        }
        val availableAspectRatio = availableWidth.value / availableHeight.value
        val imageWidth: Dp
        val imageHeight: Dp
        if (fitWidth || safeAspectRatio >= availableAspectRatio) {
            imageWidth = availableWidth
            imageHeight = imageWidth / safeAspectRatio
        } else {
            imageHeight = availableHeight
            imageWidth = imageHeight * safeAspectRatio
        }

        val density = LocalDensity.current
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val innerTopPx = with(density) { initialTopPadding.toPx() }
        val availableWidthPx = with(density) { availableWidth.toPx() }
        val availableHeightPx = with(density) { availableHeight.toPx() }
        val imageWidthPx = with(density) { imageWidth.toPx() }
        val imageHeightPx = with(density) { imageHeight.toPx() }
        val imageCenter = Offset(
            x = containerWidthPx / 2,
            y = innerTopPx + availableHeightPx / 2,
        )
        val currentImageWidthPx by rememberUpdatedState(imageWidthPx)
        val currentImageHeightPx by rememberUpdatedState(imageHeightPx)
        val currentAvailableWidthPx by rememberUpdatedState(availableWidthPx)
        val currentAvailableHeightPx by rememberUpdatedState(availableHeightPx)
        val currentImageCenter by rememberUpdatedState(imageCenter)

        LaunchedEffect(
            imageWidthPx,
            imageHeightPx,
            availableWidthPx,
            availableHeightPx,
            imageCenter,
        ) {
            previewState.imageScale = MinPreviewScale
            previewState.imageOffset = Offset.Zero
        }

        val gestureModifier = Modifier.pointerInput(previewKey) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (!currentGesturesEnabled) return@awaitEachGesture
                val gestureImageWidthPx = currentImageWidthPx
                val gestureImageHeightPx = currentImageHeightPx
                val gestureAvailableWidthPx = currentAvailableWidthPx
                val gestureAvailableHeightPx = currentAvailableHeightPx
                val gestureImageCenter = currentImageCenter
                val secondTapInterval = down.uptimeMillis - lastTapUpTime
                val isSecondTap = lastTapUpTime > 0L &&
                    secondTapInterval >= viewConfiguration.doubleTapMinTimeMillis &&
                    secondTapInterval <= viewConfiguration.doubleTapTimeoutMillis
                var accumulatedZoom = 1f
                var accumulatedPan = Offset.Zero
                var pastTouchSlop = false
                var handedOffToParent = false
                var dismissDragging = false
                var expandDragging = false
                var externalDragTranslation = Offset.Zero
                var externalDragTerminalDispatched = false
                var canceled = false
                var hadMultiplePointers = false
                var upTime = down.uptimeMillis
                var event: PointerEvent
                val velocityTracker = VelocityTracker()
                velocityTracker.addPosition(down.uptimeMillis, down.position)

                try {
                    do {
                        event = awaitPointerEvent()
                        var externalDragStartedThisEvent = false
                        val primaryChange = event.changes.firstOrNull()
                        upTime = primaryChange?.uptimeMillis ?: upTime
                        if (primaryChange != null) {
                            velocityTracker.addPosition(primaryChange.uptimeMillis, primaryChange.position)
                        }
                        if (!currentGesturesEnabled || event.changes.any { it.isConsumed }) {
                            canceled = true
                            break
                        }

                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        val pressedPointerCount = event.changes.count { it.pressed }
                        val previousPointerCount = event.changes.count { it.previousPressed }
                        if (pressedPointerCount > 1 || previousPointerCount > 1) {
                            hadMultiplePointers = true
                            if (dismissDragging || expandDragging) {
                                canceled = true
                                break
                            }
                        }

                        if (!pastTouchSlop) {
                            accumulatedZoom *= zoomChange
                            accumulatedPan += panChange
                            val zoomMotion = abs(1 - accumulatedZoom) *
                                event.calculateCentroidSize(useCurrent = false)
                            val panMotion = accumulatedPan.getDistance()
                            if (zoomMotion > viewConfiguration.touchSlop ||
                                panMotion > viewConfiguration.touchSlop
                            ) {
                                pastTouchSlop = true
                                dismissDragging = currentDismissGestureEnabled &&
                                    !hadMultiplePointers &&
                                    currentPreviewState.isAtRest &&
                                    accumulatedPan.y > 0f &&
                                    abs(accumulatedPan.y) > abs(accumulatedPan.x) &&
                                    currentOnDismissDragStart != null
                                expandDragging = !dismissDragging &&
                                    currentExpandGestureEnabled &&
                                    !hadMultiplePointers &&
                                    currentPreviewState.isAtRest &&
                                    accumulatedPan.y < 0f &&
                                    abs(accumulatedPan.y) > abs(accumulatedPan.x) &&
                                    currentOnExpandDragStart != null
                                if (dismissDragging || expandDragging) {
                                    externalDragStartedThisEvent = true
                                    externalDragTranslation = accumulatedPan
                                    if (dismissDragging) {
                                        currentOnDismissDragStart?.invoke(down.position)
                                        currentOnDismissDrag?.invoke(externalDragTranslation)
                                    } else {
                                        currentOnExpandDragStart?.invoke(down.position)
                                        currentOnExpandDrag?.invoke(externalDragTranslation)
                                    }
                                } else {
                                    handedOffToParent = !hadMultiplePointers &&
                                        shouldHandOffToParent(
                                            pan = accumulatedPan,
                                            imageOffset = currentPreviewState.imageOffset,
                                            imageScale = currentPreviewState.imageScale,
                                            imageWidth = gestureImageWidthPx,
                                            imageHeight = gestureImageHeightPx,
                                            availableWidth = gestureAvailableWidthPx,
                                            availableHeight = gestureAvailableHeightPx,
                                        )
                                }
                            }
                        }

                        if (dismissDragging || expandDragging) {
                            if (!externalDragStartedThisEvent) {
                                externalDragTranslation += panChange
                                if (dismissDragging) {
                                    currentOnDismissDrag?.invoke(externalDragTranslation)
                                } else {
                                    currentOnExpandDrag?.invoke(externalDragTranslation)
                                }
                            }
                            event.changes.forEach { change ->
                                if (change.positionChanged()) {
                                    change.consume()
                                }
                            }
                        } else if (pastTouchSlop && !handedOffToParent) {
                            val centroid = event.calculateCentroid(useCurrent = false)
                            val currentScale = currentPreviewState.imageScale
                            val nextScale = max(
                                MinPreviewScale,
                                min(MaxPreviewScale, currentScale * zoomChange),
                            )
                            if (nextScale == MinPreviewScale) {
                                currentPreviewState.imageScale = MinPreviewScale
                                currentPreviewState.imageOffset = Offset.Zero
                            } else {
                                val scaleChange = nextScale / currentScale
                                val centroidOffset = if (centroid == Offset.Unspecified) {
                                    Offset.Zero
                                } else {
                                    centroid - gestureImageCenter
                                }
                                val nextOffset = currentPreviewState.imageOffset * scaleChange +
                                    centroidOffset * (1 - scaleChange) +
                                    panChange
                                currentPreviewState.imageScale = nextScale
                                currentPreviewState.imageOffset = limitImageOffset(
                                    offset = nextOffset,
                                    imageScale = nextScale,
                                    imageWidth = gestureImageWidthPx,
                                    imageHeight = gestureImageHeightPx,
                                    availableWidth = gestureAvailableWidthPx,
                                    availableHeight = gestureAvailableHeightPx,
                                )
                            }
                            event.changes.forEach { change ->
                                if (change.positionChanged()) {
                                    change.consume()
                                }
                            }
                        }
                    } while (!handedOffToParent && event.changes.any { it.pressed })

                    if (dismissDragging || expandDragging) {
                        externalDragTerminalDispatched = true
                        if (canceled) {
                            if (dismissDragging) {
                                currentOnDismissDragCancel?.invoke()
                            } else {
                                currentOnExpandDragCancel?.invoke()
                            }
                        } else {
                            val velocityY = velocityTracker.calculateVelocity().y
                            if (dismissDragging) {
                                currentOnDismissDragEnd?.invoke(externalDragTranslation, velocityY)
                            } else {
                                currentOnExpandDragEnd?.invoke(externalDragTranslation, velocityY)
                            }
                        }
                        lastTapUpTime = 0L
                    } else if (!canceled && !pastTouchSlop && !hadMultiplePointers) {
                        if (isSecondTap) {
                            currentPreviewState.imageScale = MinPreviewScale
                            currentPreviewState.imageOffset = Offset.Zero
                            lastTapUpTime = 0L
                        } else if (upTime - down.uptimeMillis <= viewConfiguration.longPressTimeoutMillis) {
                            lastTapUpTime = upTime
                        }
                    } else {
                        lastTapUpTime = 0L
                    }
                } finally {
                    if ((dismissDragging || expandDragging) && !externalDragTerminalDispatched) {
                        lastTapUpTime = 0L
                        if (dismissDragging) {
                            currentOnDismissDragCancel?.invoke()
                        } else {
                            currentOnExpandDragCancel?.invoke()
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(gestureModifier)
                .padding(
                    top = initialTopPadding,
                    start = initialHorizontalPadding,
                    end = initialHorizontalPadding,
                    bottom = initialBottomPadding,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = (if (fitWidth) {
                    Modifier.requiredSize(width = imageWidth, height = imageHeight)
                } else {
                    Modifier.size(width = imageWidth, height = imageHeight)
                })
                    .onGloballyPositioned { coordinates ->
                        currentOnImageBoundsChanged?.invoke(coordinates.boundsInWindow())
                    }
                    .graphicsLayer {
                        scaleX = previewState.imageScale
                        scaleY = previewState.imageScale
                        translationX = previewState.imageOffset.x
                        translationY = previewState.imageOffset.y
                    },
            ) {
                imageContent(imageWidth, imageHeight)
            }
        }
    }
}

private fun shouldHandOffToParent(
    pan: Offset,
    imageOffset: Offset,
    imageScale: Float,
    imageWidth: Float,
    imageHeight: Float,
    availableWidth: Float,
    availableHeight: Float,
): Boolean {
    val maxHorizontalOffset = max(0f, (imageWidth * imageScale - availableWidth) / 2)
    val maxVerticalOffset = max(0f, (imageHeight * imageScale - availableHeight) / 2)
    return if (abs(pan.x) >= abs(pan.y)) {
        pan.x > 0f && imageOffset.x >= maxHorizontalOffset - EdgeTolerancePx ||
            pan.x < 0f && imageOffset.x <= -maxHorizontalOffset + EdgeTolerancePx
    } else {
        pan.y > 0f && imageOffset.y >= maxVerticalOffset - EdgeTolerancePx ||
            pan.y < 0f && imageOffset.y <= -maxVerticalOffset + EdgeTolerancePx
    }
}

private fun limitImageOffset(
    offset: Offset,
    imageScale: Float,
    imageWidth: Float,
    imageHeight: Float,
    availableWidth: Float,
    availableHeight: Float,
): Offset {
    val maxHorizontalOffset = max(0f, (imageWidth * imageScale - availableWidth) / 2)
    val maxVerticalOffset = max(0f, (imageHeight * imageScale - availableHeight) / 2)
    return Offset(
        x = max(-maxHorizontalOffset, min(maxHorizontalOffset, offset.x)),
        y = max(-maxVerticalOffset, min(maxVerticalOffset, offset.y)),
    )
}
