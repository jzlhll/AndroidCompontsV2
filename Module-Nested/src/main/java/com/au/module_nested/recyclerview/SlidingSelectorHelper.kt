package com.au.module_nested.recyclerview

import android.animation.TimeInterpolator
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.max
import kotlin.math.min

/** 横向滑动时连续选择 RecyclerView 数据项，并按需将静止长按交给页面处理。 */
class SlidingSelectorHelper(
    context: Context,
    private val adapter: SlideSelectAdapter,
    private val longPressTimeout: Long = ViewConfiguration.getLongPressTimeout().toLong(),
    private val positionResolver: (RecyclerView.ViewHolder) -> Int = { it.bindingAdapterPosition },
    private val onItemLongPress: ((Int) -> Unit)? = null,
) : RecyclerView.SimpleOnItemTouchListener() {
    companion object {
        /** 安装滑选监听。 */
        fun install(
            recyclerView: RecyclerView,
            adapter: SlideSelectAdapter,
            longPressTimeout: Long = ViewConfiguration.getLongPressTimeout().toLong(),
            positionResolver: (RecyclerView.ViewHolder) -> Int = { it.bindingAdapterPosition },
            onItemLongPress: ((Int) -> Unit)? = null,
        ): SlidingSelectorHelper {
            val helper = SlidingSelectorHelper(
                context = recyclerView.context.applicationContext,
                adapter = adapter,
                longPressTimeout = longPressTimeout,
                positionResolver = positionResolver,
                onItemLongPress = onItemLongPress,
            )
            recyclerView.addOnItemTouchListener(helper)
            return helper
        }

        /** 卸载滑选监听。 */
        fun uninstall(recyclerView: RecyclerView, helper: SlidingSelectorHelper) {
            helper.finishGesture()
            recyclerView.removeOnItemTouchListener(helper)
            helper.recyclerView = null
        }
    }

    private val density = context.resources.displayMetrics.density
    private val handler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val slidingRunnable = SlidingRunnable()
    private val changedByCurrentGesture = mutableSetOf<Int>()
    private var recyclerView: RecyclerView? = null
    private var isLongPress = false
    private var isLongPressPreview = false
    private var consumeCurrentGesture = false
    private var downX = 0f
    private var downY = 0f
    private var touchX = 0f
    private var touchY = 0f
    private var firstSelectedPosition = RecyclerView.NO_POSITION
    private var lastSelectedPosition = RecyclerView.NO_POSITION
    private var gestureSelectedState: Boolean? = null

    /** 是否允许开始滑选；关闭时立即结束当前手势。 */
    var enableSliding = true
        set(value) {
            field = value
            if (!value) {
                stop()
            }
        }

    /** 手指进入 RecyclerView 顶部或底部此范围时开始自动滚动。 */
    var scrollThresholdValue = 80 * density

    /** 自动滚动的基础步长。 */
    var scrollStepValue = (3 * density).toInt()

    /** 根据手指接近边缘的程度计算自动滚动步长。 */
    var scrollStepValueInterpolator: TimeInterpolator = TimeInterpolator { ratio ->
        scrollStepValue + when {
            ratio > 0.9f -> 5f
            ratio > 0.8f -> 3f
            ratio > 0.5f -> 2f
            ratio > 0.3f -> 1f
            else -> 0f
        } * scrollStepValue
    }

    private val longPressRunnable = Runnable {
        val currentRecyclerView = recyclerView ?: return@Runnable
        val position = findAdapterPosition(currentRecyclerView, downX, downY)
        if (!enableSliding || !currentRecyclerView.isAttachedToWindow ||
            position == RecyclerView.NO_POSITION) {
            return@Runnable
        }
        if (onItemLongPress != null) {
            isLongPressPreview = true
            consumeCurrentGesture = true
            currentRecyclerView.parent?.requestDisallowInterceptTouchEvent(true)
            currentRecyclerView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onItemLongPress.invoke(position)
            return@Runnable
        }
        isLongPress = true
        consumeCurrentGesture = true
        currentRecyclerView.parent?.requestDisallowInterceptTouchEvent(true)
        touchX = downX
        touchY = downY
        selectItem()
        updateEdgeScroll()
    }

    override fun onInterceptTouchEvent(recyclerView: RecyclerView, event: MotionEvent): Boolean {
        this.recyclerView = recyclerView
        val wasConsuming = consumeCurrentGesture && event.actionMasked != MotionEvent.ACTION_DOWN
        handleEvent(event)
        return wasConsuming || consumeCurrentGesture
    }

    override fun onTouchEvent(recyclerView: RecyclerView, event: MotionEvent) {
        this.recyclerView = recyclerView
        handleEvent(event)
    }

    private fun handleEvent(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                handler.removeCallbacks(longPressRunnable)
                consumeCurrentGesture = false
                resetSelectionRange()
                downX = event.x
                downY = event.y
                if (enableSliding) {
                    handler.postDelayed(longPressRunnable, longPressTimeout)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!enableSliding || isLongPressPreview) return
                if (!isLongPress) {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (isHorizontalSliding(dx, dy)) {
                        handler.removeCallbacks(longPressRunnable)
                        isLongPress = true
                        consumeCurrentGesture = true
                        recyclerView?.parent?.requestDisallowInterceptTouchEvent(true)
                        touchX = event.x
                        touchY = event.y
                        selectItem()
                        updateEdgeScroll()
                    } else if (dy * dy > touchSlop * touchSlop) {
                        handler.removeCallbacks(longPressRunnable)
                    }
                } else {
                    touchX = event.x
                    touchY = event.y
                    updateEdgeScroll()
                }
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            -> finishGesture()
        }
    }

    private fun isHorizontalSliding(dx: Float, dy: Float): Boolean {
        return kotlin.math.abs(dx) > touchSlop && kotlin.math.abs(dx) > kotlin.math.abs(dy)
    }

    private fun updateEdgeScroll() {
        if (!enableSliding) return
        val currentRecyclerView = recyclerView ?: return
        if (scrollThresholdValue <= 0f) {
            slidingRunnable.direction = 0
            selectItem()
            return
        }

        var stepValue = scrollStepValue
        val topThreshold = currentRecyclerView.paddingTop + scrollThresholdValue
        val bottomThreshold = currentRecyclerView.height -
            currentRecyclerView.paddingBottom -
            scrollThresholdValue
        val distanceFromEdge = when {
            touchY > bottomThreshold -> {
                slidingRunnable.direction = 1
                max(currentRecyclerView.height - currentRecyclerView.paddingBottom - touchY, 0f)
            }
            touchY < topThreshold -> {
                slidingRunnable.direction = -1
                max(touchY - currentRecyclerView.paddingTop, 0f)
            }
            else -> {
                slidingRunnable.direction = 0
                selectItem()
                -1f
            }
        }
        if (distanceFromEdge >= 0f) {
            val ratio = 1 - distanceFromEdge / scrollThresholdValue
            stepValue = scrollStepValueInterpolator.getInterpolation(ratio).toInt()
        }
        slidingRunnable.currentScrollStep = stepValue
    }

    private fun selectItem() {
        if (!enableSliding) return
        val currentRecyclerView = recyclerView ?: return
        val currentPosition = findAdapterPosition(currentRecyclerView, touchX, touchY)
        if (currentPosition !in 0 until adapter.itemCount) return

        if (firstSelectedPosition == RecyclerView.NO_POSITION) {
            firstSelectedPosition = currentPosition
            lastSelectedPosition = currentPosition
            val selected = !adapter.isItemSelected(currentPosition)
            gestureSelectedState = selected
            updateRange(currentPosition, currentPosition, selected)
            return
        }
        if (currentPosition == lastSelectedPosition) return

        val selected = gestureSelectedState ?: return
        val rangeStart = min(firstSelectedPosition, currentPosition)
        val rangeEnd = max(firstSelectedPosition, currentPosition)
        val previousStart = min(firstSelectedPosition, lastSelectedPosition)
        val previousEnd = max(firstSelectedPosition, lastSelectedPosition)
        updateRange(rangeStart, rangeEnd, selected)
        restoreRemovedRange(previousStart, previousEnd, rangeStart, rangeEnd, selected)
        lastSelectedPosition = currentPosition
    }

    private fun findAdapterPosition(recyclerView: RecyclerView, x: Float, y: Float): Int {
        val child = recyclerView.findChildViewUnder(x, y) ?: return RecyclerView.NO_POSITION
        val holder = recyclerView.findContainingViewHolder(child) ?: return RecyclerView.NO_POSITION
        return positionResolver(holder)
    }

    private fun updateRange(from: Int, to: Int, selected: Boolean) {
        val start = min(from, to)
        val end = max(from, to)
        for (position in start..end) {
            if (position in 0 until adapter.itemCount && adapter.isItemSelected(position) != selected) {
                adapter.setItemSelected(position, selected)
                if (adapter.isItemSelected(position) == selected) {
                    changedByCurrentGesture.add(position)
                }
            }
        }
    }

    private fun restoreRemovedRange(
        from: Int,
        to: Int,
        keepStart: Int,
        keepEnd: Int,
        selected: Boolean,
    ) {
        val start = min(from, to)
        val end = max(from, to)
        for (position in start..end) {
            if (position !in keepStart..keepEnd &&
                position in changedByCurrentGesture &&
                position in 0 until adapter.itemCount &&
                adapter.isItemSelected(position) == selected
            ) {
                adapter.setItemSelected(position, !selected)
                if (adapter.isItemSelected(position) != selected) {
                    changedByCurrentGesture.remove(position)
                }
            }
        }
    }

    /** 停止自动滚动并结束当前滑选手势。 */
    fun stop() {
        handler.removeCallbacks(longPressRunnable)
        slidingRunnable.direction = 0
        isLongPress = false
        isLongPressPreview = false
        recyclerView?.parent?.requestDisallowInterceptTouchEvent(false)
        resetSelectionRange()
    }

    private fun finishGesture() {
        stop()
        consumeCurrentGesture = false
    }

    private fun resetSelectionRange() {
        firstSelectedPosition = RecyclerView.NO_POSITION
        lastSelectedPosition = RecyclerView.NO_POSITION
        gestureSelectedState = null
        changedByCurrentGesture.clear()
    }

    private inner class SlidingRunnable : Runnable {
        var direction = 0
            set(value) {
                val oldValue = field
                field = value
                if (value == 0) {
                    recyclerView?.removeCallbacks(this)
                } else if (oldValue != value) {
                    recyclerView?.removeCallbacks(this)
                    recyclerView?.postOnAnimation(this)
                }
            }

        var currentScrollStep = scrollStepValue

        override fun run() {
            val currentRecyclerView = recyclerView
            if (!enableSliding || currentRecyclerView == null || !currentRecyclerView.isAttachedToWindow || direction == 0) {
                direction = 0
                return
            }
            currentRecyclerView.scrollBy(0, if (direction > 0) currentScrollStep else -currentScrollStep)
            if (!enableSliding || direction == 0) return
            selectItem()
            currentRecyclerView.postOnAnimation(this)
        }
    }
}
