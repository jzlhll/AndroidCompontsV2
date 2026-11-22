package com.allan.mydroid.views

import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.core.animation.doOnEnd

/** 在同一个 Fragment 内按页面前进、返回的位移移动底页与覆盖页。 */
class SlidingOverlayTransition(
    private val content: ViewGroup,
    private val overlayHost: View,
    private val overlay: View,
    private val onOverlayVisibilityChanged: (Boolean) -> Unit,
) {
    private var progress = 0f
    private var animator: ValueAnimator? = null
    private val onHostLayoutChanged = View.OnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
        if (right - left != oldRight - oldLeft && overlayHost.visibility == View.VISIBLE) {
            updateTranslations()
        }
    }

    init {
        overlayHost.addOnLayoutChangeListener(onHostLayoutChanged)
    }

    fun setExpanded(expanded: Boolean, animated: Boolean = true) {
        cancelAnimation()
        val endProgress = if (expanded) 1f else 0f
        if (!animated || progress == endProgress || overlayHost.width == 0) {
            finish(expanded)
            return
        }

        if (expanded) {
            updateTranslations()
            setOverlayVisible(true)
        }
        val transition = ValueAnimator.ofFloat(progress, endProgress)
        transition.duration = 300L
        transition.interpolator = DecelerateInterpolator()
        transition.addUpdateListener {
            progress = it.animatedValue as Float
            updateTranslations()
        }
        transition.doOnEnd {
            if (animator === transition) {
                animator = null
                finish(expanded)
            }
        }
        animator = transition
        transition.start()
    }

    fun dispose() {
        cancelAnimation()
        overlayHost.removeOnLayoutChangeListener(onHostLayoutChanged)
        content.translationX = 0f
        overlay.translationX = 0f
    }

    private fun finish(expanded: Boolean) {
        progress = if (expanded) 1f else 0f
        if (expanded) {
            updateTranslations()
        } else {
            content.translationX = 0f
            overlay.translationX = 0f
        }
        setOverlayVisible(expanded)
    }

    private fun updateTranslations() {
        val width = overlayHost.width
        content.translationX = -width * progress * 0.5f
        overlay.translationX = width * (1f - progress)
    }

    private fun setOverlayVisible(visible: Boolean) {
        if (visible == (overlayHost.visibility == View.VISIBLE)) return
        content.importantForAccessibility = if (visible) {
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        } else {
            View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        }
        content.descendantFocusability = if (visible) {
            ViewGroup.FOCUS_BLOCK_DESCENDANTS
        } else {
            ViewGroup.FOCUS_BEFORE_DESCENDANTS
        }
        if (visible) content.findFocus()?.clearFocus()
        overlayHost.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        onOverlayVisibilityChanged(visible)
    }

    private fun cancelAnimation() {
        val previous = animator
        animator = null
        previous?.cancel()
    }
}
