package com.au.audiorecordplayer.particle

import android.os.Build
import android.view.RoundedCorner
import android.view.View
import androidx.annotation.RequiresApi
import kotlin.math.max

@RequiresApi(Build.VERSION_CODES.S)
fun View.calRoundedCornerPadding(defaultCornerRadius: Float, fixRatio: Float): Float {
    val insets = rootWindowInsets
    val defaultRadius = defaultCornerRadius * context.resources.displayMetrics.density

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && insets != null) {
        val topLeft = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
        val bottomRight = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)

        val systemRadius = max(topLeft?.radius ?: 0, bottomRight?.radius ?: 0).toFloat()
        return max(systemRadius, defaultRadius) * fixRatio
    }

    return defaultRadius * fixRatio
}