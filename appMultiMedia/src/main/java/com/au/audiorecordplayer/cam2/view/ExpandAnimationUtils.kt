package com.au.audiorecordplayer.cam2.view

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator

object ExpandAnimationUtils {
    
    fun createExpandAnimator(view: View): AnimatorSet {
        val scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 0f, 1f)
        val scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 0f, 1f)
        val alpha = ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f)
        
        return AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 300
            interpolator = OvershootInterpolator()
        }
    }
    
    fun createCollapseAnimator(view: View): AnimatorSet {
        val scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 0f)
        val scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 0f)
        val alpha = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0f)
        
        return AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 300
            interpolator = AccelerateInterpolator()
        }
    }
}