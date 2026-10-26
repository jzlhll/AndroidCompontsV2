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

    /** 描述文字 85% 透明度版本（对应源项目 D9）。 */
    val TextDescD9 @Composable @ReadOnlyComposable get() = TextDesc.copy(alpha = 0.85f)

    /** 深灰文字（无对应资源）。 */
    val TextDarkGray get() = Color(0xFF666666)

    /** 浅描述文字（无对应资源）。 */
    val TextLightDesc get() = Color(0xFFAAAAAA)

    /** 警示红，对应 color_warn_btn_bg。 */
    val Red @Composable @ReadOnlyComposable get() = colorResource(R.color.color_warn_btn_bg)

    /** 通用橙色强调色（无对应资源）。 */
    val Orange get() = Color(0xFFFF7D2D)

    /** 橙色 40% 透明度，用于禁用态。 */
    val Orange40p get() = Orange.copy(alpha = 0.4f)

    /** 蓝色链接文字（无对应资源）。 */
    val BlueLink get() = Color(0xFF3478F6)

    /** 渐变模糊层的深色遮罩基色（无对应资源）。 */
    val DarkOverlay get() = Color(0xFF171717)

    val ShadowDefault @Composable @ReadOnlyComposable get() = colorResource(R.color.color_shadow_default)
    val Placeholder @Composable @ReadOnlyComposable get() = colorResource(R.color.color_switch_block_sel_dis_bg)

    /** 阴影白底块背景，对应 color_shadow_white_block_background（80% 白）。 */
    val WhiteBlockBackground @Composable @ReadOnlyComposable get() = colorResource(R.color.color_shadow_white_block_background)

    /** Backdrop blur 浮层背景，与 WhiteBlockBackground 同色。 */
    val White80Percent @Composable @ReadOnlyComposable get() = colorResource(R.color.color_shadow_white_block_background)

    /** 圆角卡片轻阴影色（无对应资源，保持与旧 composeCollectionCardShadow 一致）。 */
    val RoundedCardShadow get() = Color(0x0A404040)
}
