package com.au.module_androiduiex.styles

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Compose 文本样式。 */
object ComposeTypography {
    val FontToolbarB = composeTextStyle(20.sp, (-4).sp, fontFamily = SemiBoldFontFamily)
    val Font16B = composeTextStyle(16.sp, fontFamily = SemiBoldFontFamily)
    val Font16Desc = composeTextStyle(16.sp, color = ComposeColors.TextDescD9)
    val Font16MDesc = composeTextStyle(16.sp, color = ComposeColors.TextDescD9, fontFamily = MediumFontFamily)
    val Font16Red = composeTextStyle(16.sp, color = ComposeColors.Red)
    val Font16 = composeTextStyle(16.sp)
    val Font16M = composeTextStyle(16.sp, fontFamily = MediumFontFamily)
    val Font18M = composeTextStyle(18.sp, fontFamily = MediumFontFamily)
    val Font14 = composeTextStyle(14.sp)
    val Font14Desc = composeTextStyle(14.sp, color = ComposeColors.TextDescD9)
    val Font14Desc91 = composeTextStyle(14.sp, color = ComposeColors.TextSecondary)
    val Font20M = composeTextStyle(20.sp, fontFamily = MediumFontFamily)
    val Font24M = composeTextStyle(24.sp, fontFamily = MediumFontFamily)
    val Font20 = composeTextStyle(20.sp)
    val Font20B = composeTextStyle(20.sp, fontFamily = SemiBoldFontFamily)
    val Font22B = composeTextStyle(22.sp, fontFamily = SemiBoldFontFamily)
    val Font22M = composeTextStyle(22.sp, fontFamily = MediumFontFamily)
    val Font14Dp = composeTextStyle(14.sp)
    val Font14M = composeTextStyle(14.sp, fontFamily = MediumFontFamily)
    val Font14B = composeTextStyle(14.sp, fontFamily = SemiBoldFontFamily)
    val Font12Desc = composeTextStyle(12.sp, color = ComposeColors.TextDescD9)
    val Font12 = composeTextStyle(12.sp)
    val Font12M = composeTextStyle(12.sp, fontFamily = MediumFontFamily)
    val Font13C0 = composeTextStyle(13.sp, color = ComposeColors.TextDesc)
    val Font14DescC0 = composeTextStyle(14.sp, (-1).sp, color = ComposeColors.TextDesc)
    val Font16DescC0 = composeTextStyle(16.sp, (-1).sp, color = ComposeColors.TextDesc)
    val Font14Orange = composeTextStyle(14.sp, (-1).sp, color = ComposeColors.Orange)
    val Font20OrangeM = composeTextStyle(20.sp, color = ComposeColors.Orange, fontFamily = MediumFontFamily)
    val Font12DescC0 = composeTextStyle(12.sp, color = ComposeColors.TextDesc)
    val Font12MLightDesc = composeTextStyle(12.sp, color = ComposeColors.TextLightDesc, fontFamily = MediumFontFamily)
    val Font10M = composeTextStyle(10.sp, fontFamily = MediumFontFamily)
    val Font10 = composeTextStyle(10.sp)
    val Font12BlueLink = composeTextStyle(12.sp, color = ComposeColors.BlueLink, fontFamily = MediumFontFamily)
    val Font16CancelBtn = composeTextStyle(16.sp)
    val Font16BlackBtn = composeTextStyle(16.sp, color = Color.White)
    val Font16WarnBtn = composeTextStyle(16.sp, color = Color.White)
    val FontSmallWarnBtn = composeTextStyle(13.sp, color = Color.White)
    val Font16OrangeTextBtn = composeTextStyle(16.sp, color = ComposeColors.Orange)
    val FontEdit = composeTextStyle(14.sp, (-1).sp)
    val FontLoginEdit = composeTextStyle(14.sp, (-1).sp, color = Color.White)
}

private fun composeTextStyle(
    fontSize: TextUnit,
    lineSpacingExtra: TextUnit = (-2).sp,
    color: Color = ComposeColors.TextPrimary,
    fontFamily: FontFamily = RegularFontFamily,
) = TextStyle(
    fontSize = fontSize,
    lineHeight = (fontSize.value + lineSpacingExtra.value).sp,
    fontFamily = fontFamily,
    color = color,
)
