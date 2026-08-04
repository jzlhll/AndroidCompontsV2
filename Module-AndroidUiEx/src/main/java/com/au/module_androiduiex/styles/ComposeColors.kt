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

    /** 阴影白底块背景，对应 color_shadow_white_block_background（80% 白）。 */
    val WhiteBlockBackground @Composable @ReadOnlyComposable get() = colorResource(R.color.color_shadow_white_block_background)

    /** Backdrop blur 浮层背景，与 WhiteBlockBackground 同色。 */
    val White80Percent @Composable @ReadOnlyComposable get() = colorResource(R.color.color_shadow_white_block_background)

    /** 圆角卡片轻阴影色（无对应资源，保持与旧 composeCollectionCardShadow 一致）。 */
    val RoundedCardShadow get() = Color(0x0A404040)

    /** 描述文字色 50% 透明度，用于模糊圆覆盖层。 */
    val TextDesc50Percent @Composable @ReadOnlyComposable get() = colorResource(R.color.color_text_desc).copy(alpha = 0.5f)
}
