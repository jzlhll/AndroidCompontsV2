package com.au.module_androiduiex.styles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

object ComposeTypography {
    val FontToolbarB @Composable @ReadOnlyComposable get() = composeTextStyle(20.sp, (-4).sp, fontFamily = BoldFontFamily)
    val Font16B @Composable @ReadOnlyComposable get() = composeTextStyle(16.sp, fontFamily = BoldFontFamily)
    val Font16 @Composable @ReadOnlyComposable get() = composeTextStyle(16.sp)
    val Font16M @Composable @ReadOnlyComposable get() = composeTextStyle(16.sp, fontFamily = MediumFontFamily)
    val Font18M @Composable @ReadOnlyComposable get() = composeTextStyle(18.sp, fontFamily = MediumFontFamily)
    val Font14sp @Composable @ReadOnlyComposable get() = composeTextStyle(14.sp)
    val Font14Secondary @Composable @ReadOnlyComposable get() = composeTextStyle(14.sp, color = ComposeColors.TextSecondary)
    val Font14MSecondary @Composable @ReadOnlyComposable get() = composeTextStyle(14.sp, color = ComposeColors.TextSecondary, fontFamily = MediumFontFamily)
    val Font20M @Composable @ReadOnlyComposable get() = composeTextStyle(20.sp, fontFamily = MediumFontFamily)
    val Font24M @Composable @ReadOnlyComposable get() = composeTextStyle(24.sp, fontFamily = MediumFontFamily)
    val Font20sp @Composable @ReadOnlyComposable get() = composeTextStyle(20.sp)
    val Font20B @Composable @ReadOnlyComposable get() = composeTextStyle(20.sp, fontFamily = BoldFontFamily)
    val Font22B @Composable @ReadOnlyComposable get() = composeTextStyle(22.sp, fontFamily = BoldFontFamily)
    val Font22M @Composable @ReadOnlyComposable get() = composeTextStyle(22.sp, fontFamily = MediumFontFamily)
    val Font14Dp @Composable @ReadOnlyComposable get() = composeTextStyle(14.sp) // styles.xml 为 14dp，Compose 字号仅支持 sp
    val Font14M @Composable @ReadOnlyComposable get() = composeTextStyle(14.sp, fontFamily = MediumFontFamily)
    val Font14B @Composable @ReadOnlyComposable get() = composeTextStyle(14.sp, fontFamily = BoldFontFamily)
    val Font12sp @Composable @ReadOnlyComposable get() = composeTextStyle(12.sp)
    val Font12M @Composable @ReadOnlyComposable get() = composeTextStyle(12.sp, fontFamily = MediumFontFamily)
    val Font12MWhite @Composable @ReadOnlyComposable get() = composeTextStyle(12.sp, fontFamily = MediumFontFamily, color = Color.White)
    val Font13C0 @Composable @ReadOnlyComposable get() = composeTextStyle(13.sp, color = ComposeColors.TextDesc)
    val Font14DescC0 @Composable @ReadOnlyComposable get() = composeTextStyle(14.sp, (-1).sp, color = ComposeColors.TextDesc)
    val Font16DescC0 @Composable @ReadOnlyComposable get() = composeTextStyle(16.sp, (-1).sp, color = ComposeColors.TextDesc)
    val Font12DescC0 @Composable @ReadOnlyComposable get() = composeTextStyle(12.sp, color = ComposeColors.TextDesc)
    val Font10M @Composable @ReadOnlyComposable get() = composeTextStyle(10.sp, fontFamily = MediumFontFamily)
    val Font10 @Composable @ReadOnlyComposable get() = composeTextStyle(10.sp)
    val Font16CancelBtn @Composable @ReadOnlyComposable get() = composeTextStyle(16.sp)
    val Font16BlackBtn @Composable @ReadOnlyComposable get() = composeTextStyle(16.sp, color = Color.White)
    val Font14White @Composable @ReadOnlyComposable get() = composeTextStyle(14.sp, color = Color.White)
    val Font20MWhite @Composable @ReadOnlyComposable get() = composeTextStyle(20.sp, fontFamily = MediumFontFamily, color = Color.White)
    val Font16WarnBtn @Composable @ReadOnlyComposable get() = composeTextStyle(16.sp, color = Color.White)
    val FontSmallWarnBtn @Composable @ReadOnlyComposable get() = composeTextStyle(13.sp, color = Color.White)
    val FontEdit @Composable @ReadOnlyComposable get() = composeTextStyle(14.sp, (-1).sp)
    val FontLoginEdit @Composable @ReadOnlyComposable get() = composeTextStyle(14.sp, (-1).sp, color = Color.White)
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
