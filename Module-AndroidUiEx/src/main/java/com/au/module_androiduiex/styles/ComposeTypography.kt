package com.au.module_androiduiex.styles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

object ComposeTypography {
    val FontToolbarB @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(20.sp, fontFamily = SemiBoldFontFamily)
    val Font32M @Composable @ReadOnlyComposable get() = composeTextStyle(32.sp, 0.sp, fontFamily = SemiBoldFontFamily)
    val Font16B @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(16.sp, fontFamily = SemiBoldFontFamily)
    val Font16Desc @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(16.sp, color = ComposeColors.TextDescD9)
    val Font16Desc91 @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(16.sp, color = ComposeColors.TextSecondary)
    val Font18Desc @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(18.sp, color = ComposeColors.TextDescD9)
    val Font18Desc91 @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(18.sp, color = ComposeColors.TextSecondary)
    val Font16MDesc @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(16.sp, color = ComposeColors.TextDescD9, fontFamily = MediumFontFamily)
    val Font16Red @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(16.sp, color = ComposeColors.Red)
    val Font16MRed @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(16.sp, color = ComposeColors.Red, fontFamily = MediumFontFamily)
    val Font16 @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(16.sp)
    val Font16M @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(16.sp, fontFamily = MediumFontFamily)
    val Font16MWhite @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(16.sp, color = Color.White, fontFamily = MediumFontFamily)
    val Font16MDescC0 @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(16.sp, color = ComposeColors.TextDesc, fontFamily = MediumFontFamily)
    val Font16MOrange @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(16.sp, color = ComposeColors.Orange, fontFamily = MediumFontFamily)
    val Font18M @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(18.sp, fontFamily = MediumFontFamily)
    val Font14sp @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(14.sp)
    val Font13sp @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(13.sp)
    val Font14Desc @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(14.sp, color = ComposeColors.TextDescD9)
    val Font14Desc91 @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(14.sp, color = ComposeColors.TextSecondary)
    val Font14MDesc @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(14.sp, color = ComposeColors.TextDescD9, fontFamily = MediumFontFamily)
    val Font14MDesc91 @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(14.sp, color = ComposeColors.TextSecondary, fontFamily = MediumFontFamily)
    val Font20M @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(20.sp, fontFamily = MediumFontFamily)
    val Font24M @Composable @ReadOnlyComposable get() = composeTextStyle(24.sp, fontFamily = MediumFontFamily)
    val Font20sp @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(20.sp)
    val Font20B @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(20.sp, fontFamily = SemiBoldFontFamily)
    val Font22B @Composable @ReadOnlyComposable get() = composeTextStyle(22.sp, fontFamily = SemiBoldFontFamily)
    val Font22M @Composable @ReadOnlyComposable get() = composeTextStyle(22.sp, fontFamily = MediumFontFamily)
    val Font22MDesc73 @Composable @ReadOnlyComposable get() = composeTextStyle(22.sp, color = ComposeColors.TextDarkGray, fontFamily = MediumFontFamily)
    val Font14Dp @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(14.sp) // styles.xml 为 14dp，Compose 字号仅支持 sp
    val Font14M @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(14.sp, fontFamily = MediumFontFamily)
    val Font14MOrange @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(14.sp, color = ComposeColors.Orange, fontFamily = MediumFontFamily)
    val Font14MWhite @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(14.sp, fontFamily = MediumFontFamily, color = Color.White)
    val Font14B @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(14.sp, fontFamily = SemiBoldFontFamily)
    val Font12Desc @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(12.sp, color = ComposeColors.TextDescD9)
    val Font12sp @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(12.sp)
    val Font12M @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(12.sp, fontFamily = MediumFontFamily)
    val Font12MWhite @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(12.sp, fontFamily = MediumFontFamily, color = Color.White)
    val Font13C0 @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(13.sp, color = ComposeColors.TextDesc)
    val Font14DescC0 @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(14.sp, color = ComposeColors.TextDesc)
    val Font16DescC0 @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(16.sp, color = ComposeColors.TextDesc)
    val Font14Orange @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(14.sp, color = ComposeColors.Orange)
    val Font20OrangeM @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(20.sp, color = ComposeColors.Orange, fontFamily = MediumFontFamily)
    val Font24OrangeM @Composable @ReadOnlyComposable get() = composeTextStyle(24.sp, color = ComposeColors.Orange, fontFamily = MediumFontFamily)
    val Font12DescC0 @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(12.sp, color = ComposeColors.TextDesc)
    val Font12MLightDesc @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(12.sp, color = ComposeColors.TextLightDesc, fontFamily = MediumFontFamily)
    val Font10M @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(10.sp, fontFamily = MediumFontFamily)
    val Font10 @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(10.sp)
    val Font12BlueLink @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(12.sp, color = ComposeColors.BlueLink, fontFamily = MediumFontFamily)
    val Font16CancelBtn @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(16.sp)
    val Font16BlackBtn @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(16.sp, color = Color.White)
    val Font14White @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(14.sp, color = Color.White)
    val Font16White @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(16.sp, color = Color.White)
    val Font20MWhite @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(20.sp, fontFamily = MediumFontFamily, color = Color.White)
    val Font16WarnBtn @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(16.sp, color = Color.White)
    val FontSmallWarnBtn @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(13.sp, color = Color.White)
    val Font16OrangeTextBtn @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(16.sp, color = ComposeColors.Orange)
    val Font16OrangeDisableTextBtn @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(16.sp, color = ComposeColors.Orange40p)
    val FontEdit @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(14.sp)
    val FontLoginEdit @Composable @ReadOnlyComposable get() = composeTextStyleWithoutLineHeight(14.sp, color = Color.White)
}

@Composable
@ReadOnlyComposable
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

@Composable
@ReadOnlyComposable
private fun composeTextStyleWithoutLineHeight(
    fontSize: TextUnit,
    color: Color = ComposeColors.TextPrimary,
    fontFamily: FontFamily = RegularFontFamily,
) = TextStyle(
    fontSize = fontSize,
    fontFamily = fontFamily,
    color = color,
)
