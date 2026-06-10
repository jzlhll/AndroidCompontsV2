package com.au.module_androiduiex.styles

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Compose 文本样式。 */
object UiExTypography {
    val ToolbarB = uiExTextStyle(20.sp, (-4).sp, fontWeight = FontWeight.SemiBold)
    val Text16B = uiExTextStyle(16.sp, fontWeight = FontWeight.SemiBold)
    val Text16Desc = uiExTextStyle(16.sp, color = UiExColors.TextDescD9)
    val Text16MDesc = uiExTextStyle(16.sp, color = UiExColors.TextDescD9, fontWeight = FontWeight.Medium)
    val Text16Red = uiExTextStyle(16.sp, color = UiExColors.Red)
    val Text16 = uiExTextStyle(16.sp)
    val Text16M = uiExTextStyle(16.sp, fontWeight = FontWeight.Medium)
    val Text18M = uiExTextStyle(18.sp, fontWeight = FontWeight.Medium)
    val Text14 = uiExTextStyle(14.sp)
    val Text14Desc = uiExTextStyle(14.sp, color = UiExColors.TextDescD9)
    val Text14Desc91 = uiExTextStyle(14.sp, color = UiExColors.TextSecondary)
    val Text20M = uiExTextStyle(20.sp, fontWeight = FontWeight.Medium)
    val Text24M = uiExTextStyle(24.sp, fontWeight = FontWeight.Medium)
    val Text20 = uiExTextStyle(20.sp)
    val Font20B = uiExTextStyle(20.sp, fontWeight = FontWeight.SemiBold)
    val Text22B = uiExTextStyle(22.sp, fontWeight = FontWeight.SemiBold)
    val Text22M = uiExTextStyle(22.sp, fontWeight = FontWeight.Medium)
    val Text14Dp = uiExTextStyle(14.sp)
    val Text14M = uiExTextStyle(14.sp, fontWeight = FontWeight.Medium)
    val Text14B = uiExTextStyle(14.sp, fontWeight = FontWeight.SemiBold)
    val Text12Desc = uiExTextStyle(12.sp, color = UiExColors.TextDescD9)
    val Text12 = uiExTextStyle(12.sp)
    val Font12M = uiExTextStyle(12.sp, fontWeight = FontWeight.Medium)
    val Font13C0 = uiExTextStyle(13.sp, color = UiExColors.TextDesc)
    val Text14DescC0 = uiExTextStyle(14.sp, (-1).sp, color = UiExColors.TextDesc)
    val Text16DescC0 = uiExTextStyle(16.sp, (-1).sp, color = UiExColors.TextDesc)
    val Text14Orange = uiExTextStyle(14.sp, (-1).sp, color = UiExColors.Orange)
    val Text20OrangeM = uiExTextStyle(20.sp, color = UiExColors.Orange, fontWeight = FontWeight.Medium)
    val Text12DescC0 = uiExTextStyle(12.sp, color = UiExColors.TextDesc)
    val Text12MLightDesc = uiExTextStyle(12.sp, color = UiExColors.TextLightDesc, fontWeight = FontWeight.Medium)
    val Text10M = uiExTextStyle(10.sp, fontWeight = FontWeight.Medium)
    val Text10 = uiExTextStyle(10.sp)
    val Text12BlueLink = uiExTextStyle(12.sp, color = UiExColors.BlueLink, fontWeight = FontWeight.Medium)
    val Text16CancelBtn = uiExTextStyle(16.sp)
    val Text16BlackBtn = uiExTextStyle(16.sp, color = Color.White)
    val Text16WarnBtn = uiExTextStyle(16.sp, color = Color.White)
    val SmallWarnBtn = uiExTextStyle(13.sp, color = Color.White)
    val Text16OrangeTextBtn = uiExTextStyle(16.sp, color = UiExColors.Orange)
    val Edit = uiExTextStyle(14.sp, (-1).sp)
    val LoginEdit = uiExTextStyle(14.sp, (-1).sp, color = Color.White)
}

private fun uiExTextStyle(
    fontSize: TextUnit,
    lineSpacingExtra: TextUnit = (-2).sp,
    color: Color = UiExColors.TextPrimary,
    fontWeight: FontWeight = FontWeight.Normal,
) = TextStyle(
    fontSize = fontSize,
    lineHeight = (fontSize.value + lineSpacingExtra.value).sp,
    fontWeight = fontWeight,
    color = color,
)
