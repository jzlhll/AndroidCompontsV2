package com.allan.androidlearning.picwall

import android.content.Context
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.widget.FrameLayout
import com.au.module_android.utilsmedia.UriParsedInfo
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

class PicStackLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val MAX_VISIBLE_COUNT = 5
        private const val CARD_WIDTH_RATIO = 0.58f
        private const val CARD_HEIGHT_RATIO = 0.76f
        private const val FRAME_STROKE_WIDTH_DP = 10f
        private const val ROTATION_LIMIT_DEGREE = 8f
        private const val ROTATION_MIN_DEGREE = 2f
        private const val MAX_DRAG_ROTATION = 12f
        private const val DISMISS_DISTANCE_RATIO = 0.22f
        private const val DISMISS_VELOCITY = 1800f
        private const val MAX_OVERLAP_RATIO = 0.84f
        private const val MIN_CENTER_DISTANCE_RATIO = 0.16f
        private const val RANDOM_TRY_COUNT = 18
    }

    private data class CardPlacement(
        val centerX: Float,
        val centerY: Float,
        val rotation: Float,
        val bounds: RectF,
    )

    private val allItems = ArrayList<UriParsedInfo>()
    private val visibleItems = ArrayList<UriParsedInfo>()
    private var nextDataIndex = 0
    private var layoutRandomSeed = 0L

    init {
        clipChildren = false
        clipToPadding = false
    }

    /**
     * 提交卡片数据。
     *
     * @param list 待展示的数据列表
     * @return Unit
     * @throws Nothing
     */
    fun submitList(list: List<UriParsedInfo>) {
        allItems.clear()
        allItems.addAll(list)
        visibleItems.clear()
        nextDataIndex = 0
        fillVisibleItems()
        layoutRandomSeed = System.nanoTime()
        if (width > 0 && height > 0) {
            renderVisibleCards()
        } else {
            post { renderVisibleCards() }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && (w != oldw || h != oldh)) {
            renderVisibleCards()
        }
    }

    private fun fillVisibleItems() {
        while (visibleItems.size < MAX_VISIBLE_COUNT && nextDataIndex < allItems.size) {
            visibleItems.add(allItems[nextDataIndex])
            nextDataIndex += 1
        }
    }

    private fun renderVisibleCards() {
        removeAllViews()
        if (visibleItems.isEmpty() || width == 0 || height == 0) {
            return
        }

        val availableWidth = width - paddingLeft - paddingRight
        val availableHeight = height - paddingTop - paddingBottom
        if (availableWidth <= 0 || availableHeight <= 0) {
            return
        }

        val cardWidth = max(1, (availableWidth * CARD_WIDTH_RATIO).roundToInt())
        val cardHeight = max(1, (availableHeight * CARD_HEIGHT_RATIO).roundToInt())
        val placements = generatePlacements(cardWidth.toFloat(), cardHeight.toFloat())

        for (index in visibleItems.indices.reversed()) {
            val cardView = PicStackCardView(context)
            val placement = placements[index]
            val left = (placement.centerX - cardWidth / 2f).roundToInt()
            val top = (placement.centerY - cardHeight / 2f).roundToInt()
            val params = LayoutParams(cardWidth, cardHeight)
            params.leftMargin = left
            params.topMargin = top
            addView(cardView, params)
            cardView.rotation = placement.rotation
            cardView.bindData(visibleItems[index], FRAME_STROKE_WIDTH_DP)
            if (index == 0) {
                bindTopCardGesture(cardView, placement.rotation)
            }
        }
    }

    private fun generatePlacements(cardWidth: Float, cardHeight: Float): List<CardPlacement> {
        val placements = ArrayList<CardPlacement>(visibleItems.size)
        val availableWidth = (width - paddingLeft - paddingRight).toFloat()
        val availableHeight = (height - paddingTop - paddingBottom).toFloat()
        val centerX = paddingLeft + availableWidth / 2f
        val centerY = paddingTop + availableHeight / 2f
        val baseOffsets = buildBaseOffsets(availableWidth, availableHeight)
        val random = Random(layoutRandomSeed)

        visibleItems.indices.forEach { index ->
            val baseOffset = baseOffsets[min(index, baseOffsets.lastIndex)]
            val rotation = createRotation(index, random)
            var acceptedPlacement: CardPlacement? = null
            repeat(RANDOM_TRY_COUNT) {
                val jitterX = availableWidth * 0.05f
                val jitterY = availableHeight * 0.05f
                val offsetX = baseOffset.x + random.nextFloat() * jitterX * 2f - jitterX
                val offsetY = baseOffset.y + random.nextFloat() * jitterY * 2f - jitterY
                val candidate = createPlacement(
                    centerX = centerX + offsetX,
                    centerY = centerY + offsetY,
                    cardWidth = cardWidth,
                    cardHeight = cardHeight,
                    rotation = rotation,
                )
                if (isPlacementValid(candidate, placements)) {
                    acceptedPlacement = candidate
                    return@repeat
                }
            }
            val fallbackPlacement = createPlacement(
                centerX = centerX + baseOffset.x,
                centerY = centerY + baseOffset.y,
                cardWidth = cardWidth,
                cardHeight = cardHeight,
                rotation = rotation,
            )
            placements.add(acceptedPlacement ?: fallbackPlacement)
        }
        return placements
    }

    private fun buildBaseOffsets(availableWidth: Float, availableHeight: Float): List<PointF> {
        return listOf(
            PointF(0f, 0f),
            PointF(-availableWidth * 0.12f, -availableHeight * 0.06f),
            PointF(availableWidth * 0.12f, -availableHeight * 0.03f),
            PointF(-availableWidth * 0.08f, availableHeight * 0.11f),
            PointF(availableWidth * 0.09f, availableHeight * 0.09f),
        )
    }

    private fun createRotation(index: Int, random: Random): Float {
        if (index == 0) {
            return 0f
        }
        val value = random.nextFloat() * (ROTATION_LIMIT_DEGREE - ROTATION_MIN_DEGREE) + ROTATION_MIN_DEGREE
        return if (index % 2 == 1) {
            -value
        } else {
            value
        }
    }

    private fun createPlacement(
        centerX: Float,
        centerY: Float,
        cardWidth: Float,
        cardHeight: Float,
        rotation: Float,
    ): CardPlacement {
        val bounds = buildRotatedBounds(centerX, centerY, cardWidth, cardHeight, rotation)
        return CardPlacement(centerX, centerY, rotation, bounds)
    }

    private fun buildRotatedBounds(
        centerX: Float,
        centerY: Float,
        width: Float,
        height: Float,
        rotation: Float,
    ): RectF {
        val radians = Math.toRadians(rotation.toDouble())
        val cosValue = abs(cos(radians)).toFloat()
        val sinValue = abs(sin(radians)).toFloat()
        val rotatedWidth = width * cosValue + height * sinValue
        val rotatedHeight = width * sinValue + height * cosValue
        return RectF(
            centerX - rotatedWidth / 2f,
            centerY - rotatedHeight / 2f,
            centerX + rotatedWidth / 2f,
            centerY + rotatedHeight / 2f,
        )
    }

    private fun isPlacementValid(candidate: CardPlacement, existing: List<CardPlacement>): Boolean {
        val contentLeft = paddingLeft.toFloat()
        val contentTop = paddingTop.toFloat()
        val contentRight = width - paddingRight.toFloat()
        val contentBottom = height - paddingBottom.toFloat()
        if (candidate.bounds.left < contentLeft ||
            candidate.bounds.top < contentTop ||
            candidate.bounds.right > contentRight ||
            candidate.bounds.bottom > contentBottom
        ) {
            return false
        }

        existing.forEach { placed ->
            val overlapRatio = calculateOverlapRatio(candidate.bounds, placed.bounds)
            val centerDistance = hypot(candidate.centerX - placed.centerX, candidate.centerY - placed.centerY)
            val minDistance = min(candidate.bounds.width(), candidate.bounds.height()) * MIN_CENTER_DISTANCE_RATIO
            if (overlapRatio > MAX_OVERLAP_RATIO && centerDistance < minDistance) {
                return false
            }
        }
        return true
    }

    private fun calculateOverlapRatio(first: RectF, second: RectF): Float {
        val left = max(first.left, second.left)
        val top = max(first.top, second.top)
        val right = min(first.right, second.right)
        val bottom = min(first.bottom, second.bottom)
        if (left >= right || top >= bottom) {
            return 0f
        }
        val overlapArea = (right - left) * (bottom - top)
        val minArea = min(first.width() * first.height(), second.width() * second.height())
        if (minArea <= 0f) {
            return 0f
        }
        return overlapArea / minArea
    }

    private fun bindTopCardGesture(cardView: View, baseRotation: Float) {
        cardView.setOnTouchListener(object : OnTouchListener {
            private var downRawX = 0f
            private var downRawY = 0f
            private var startTranslationX = 0f
            private var startTranslationY = 0f
            private var velocityTracker: VelocityTracker? = null

            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        view.animate().cancel()
                        downRawX = event.rawX
                        downRawY = event.rawY
                        startTranslationX = view.translationX
                        startTranslationY = view.translationY
                        velocityTracker?.recycle()
                        velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        velocityTracker?.addMovement(event)
                        val moveX = event.rawX - downRawX
                        val moveY = event.rawY - downRawY
                        view.translationX = startTranslationX + moveX
                        view.translationY = startTranslationY + moveY
                        val rotationOffset = (view.translationX / max(1f, view.width.toFloat())) * MAX_DRAG_ROTATION
                        view.rotation = baseRotation + rotationOffset
                        return true
                    }

                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        velocityTracker?.addMovement(event)
                        velocityTracker?.computeCurrentVelocity(1000)
                        val xVelocity = velocityTracker?.xVelocity ?: 0f
                        val yVelocity = velocityTracker?.yVelocity ?: 0f
                        finishDrag(view, baseRotation, xVelocity, yVelocity)
                        velocityTracker?.recycle()
                        velocityTracker = null
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun finishDrag(view: View, baseRotation: Float, xVelocity: Float, yVelocity: Float) {
        val distance = hypot(view.translationX, view.translationY)
        val dismissDistance = min(width.toFloat(), height.toFloat()) * DISMISS_DISTANCE_RATIO
        val velocity = hypot(xVelocity, yVelocity)
        if (distance >= dismissDistance || velocity >= DISMISS_VELOCITY) {
            dismissTopCard(view, baseRotation, xVelocity, yVelocity)
            return
        }
        view.animate()
            .translationX(0f)
            .translationY(0f)
            .rotation(baseRotation)
            .setDuration(220L)
            .start()
    }

    private fun dismissTopCard(view: View, baseRotation: Float, xVelocity: Float, yVelocity: Float) {
        var directionX = view.translationX
        var directionY = view.translationY
        if (abs(directionX) < 1f && abs(directionY) < 1f) {
            directionX = if (abs(xVelocity) > 1f) xVelocity else width.toFloat()
            directionY = if (abs(yVelocity) > 1f) yVelocity else 0f
        } else {
            directionX += xVelocity * 0.08f
            directionY += yVelocity * 0.08f
        }

        val norm = max(1f, hypot(directionX, directionY))
        val distance = max(width.toFloat(), height.toFloat()) + max(view.width.toFloat(), view.height.toFloat())
        val targetX = directionX / norm * distance
        val targetY = directionY / norm * distance
        val targetRotation = baseRotation + (targetX / max(1f, width.toFloat())) * MAX_DRAG_ROTATION
        view.animate()
            .translationX(targetX)
            .translationY(targetY)
            .rotation(targetRotation)
            .alpha(0f)
            .setDuration(260L)
            .withEndAction {
                consumeTopCard()
            }
            .start()
    }

    private fun consumeTopCard() {
        if (visibleItems.isNotEmpty()) {
            visibleItems.removeAt(0)
        }
        fillVisibleItems()
        layoutRandomSeed = System.nanoTime()
        renderVisibleCards()
    }
}
