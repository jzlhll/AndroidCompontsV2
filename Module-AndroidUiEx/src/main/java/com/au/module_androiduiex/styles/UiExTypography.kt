package com.au.module_androiduiex.styles

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Compose 文本样式。 */
object UiExTypography {
    val FontToolbarB = uiExTextStyle(20.sp, (-4).sp, fontFamily = SemiBoldFontFamily)
    val Font16B = uiExTextStyle(16.sp, fontFamily = SemiBoldFontFamily)
    val Font16Desc = uiExTextStyle(16.sp, color = UiExColors.TextDescD9)
    val Font16MDesc = uiExTextStyle(16.sp, color = UiExColors.TextDescD9, fontFamily = MediumFontFamily)
    val Font16Red = uiExTextStyle(16.sp, color = UiExColors.Red)
    val Font16 = uiExTextStyle(16.sp)
    val Font16M = uiExTextStyle(16.sp, fontFamily = MediumFontFamily)
    val Font18M = uiExTextStyle(18.sp, fontFamily = MediumFontFamily)
    val Font14 = uiExTextStyle(14.sp)
    val Font14Desc = uiExTextStyle(14.sp, color = UiExColors.TextDescD9)
    val Font14Desc91 = uiExTextStyle(14.sp, color = UiExColors.TextSecondary)
    val Font20M = uiExTextStyle(20.sp, fontFamily = MediumFontFamily)
    val Font24M = uiExTextStyle(24.sp, fontFamily = MediumFontFamily)
    val Font20 = uiExTextStyle(20.sp)
    val Font20B = uiExTextStyle(20.sp, fontFamily = SemiBoldFontFamily)
    val Font22B = uiExTextStyle(22.sp, fontFamily = SemiBoldFontFamily)
    val Font22M = uiExTextStyle(22.sp, fontFamily = MediumFontFamily)
    val Font14Dp = uiExTextStyle(14.sp)
    val Font14M = uiExTextStyle(14.sp, fontFamily = MediumFontFamily)
    val Font14B = uiExTextStyle(14.sp, fontFamily = SemiBoldFontFamily)
    val Font12Desc = uiExTextStyle(12.sp, color = UiExColors.TextDescD9)
    val Font12 = uiExTextStyle(12.sp)
    val Font12M = uiExTextStyle(12.sp, fontFamily = MediumFontFamily)
    val Font13C0 = uiExTextStyle(13.sp, color = UiExColors.TextDesc)
    val Font14DescC0 = uiExTextStyle(14.sp, (-1).sp, color = UiExColors.TextDesc)
    val Font16DescC0 = uiExTextStyle(16.sp, (-1).sp, color = UiExColors.TextDesc)
    val Font14Orange = uiExTextStyle(14.sp, (-1).sp, color = UiExColors.Orange)
    val Font20OrangeM = uiExTextStyle(20.sp, color = UiExColors.Orange, fontFamily = MediumFontFamily)
    val Font12DescC0 = uiExTextStyle(12.sp, color = UiExColors.TextDesc)
    val Font12MLightDesc = uiExTextStyle(12.sp, color = UiExColors.TextLightDesc, fontFamily = MediumFontFamily)
    val Font10M = uiExTextStyle(10.sp, fontFamily = MediumFontFamily)
    val Font10 = uiExTextStyle(10.sp)
    val Font12BlueLink = uiExTextStyle(12.sp, color = UiExColors.BlueLink, fontFamily = MediumFontFamily)
    val Font16CancelBtn = uiExTextStyle(16.sp)
    val Font16BlackBtn = uiExTextStyle(16.sp, color = Color.White)
    val Font16WarnBtn = uiExTextStyle(16.sp, color = Color.White)
    val FontSmallWarnBtn = uiExTextStyle(13.sp, color = Color.White)
    val Font16OrangeTextBtn = uiExTextStyle(16.sp, color = UiExColors.Orange)
    val FontEdit = uiExTextStyle(14.sp, (-1).sp)
    val FontLoginEdit = uiExTextStyle(14.sp, (-1).sp, color = Color.White)
}

private fun uiExTextStyle(
    fontSize: TextUnit,
    lineSpacingExtra: TextUnit = (-2).sp,
    color: Color = UiExColors.TextPrimary,
    fontFamily: FontFamily = RegularFontFamily,
) = TextStyle(
    fontSize = fontSize,
    lineHeight = (fontSize.value + lineSpacingExtra.value).sp,
    fontFamily = fontFamily,
    color = color,
)
