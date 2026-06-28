package com.au.module_androiduiex.styles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.au.module_androidcolor.R

object ComposeColors {
    val PrimaryBg @Composable @ReadOnlyComposable get() = colorResource(R.color.colorPrimary)
    val BackgroundBg @Composable @ReadOnlyComposable get() = colorResource(R.color.windowBackground)

    val TextPrimary @Composable @ReadOnlyComposable get() = colorResource(R.color.color_text_normal)
    val TextSecondary @Composable @ReadOnlyComposable get() = colorResource(R.color.color_second_btn_text)
    val TextDesc @Composable @ReadOnlyComposable get() = colorResource(R.color.color_text_desc)

    val ShadowDefault @Composable @ReadOnlyComposable get() = colorResource(R.color.color_shadow_default)
    val Placeholder @Composable @ReadOnlyComposable get() = colorResource(R.color.color_switch_block_sel_dis_bg)
}
