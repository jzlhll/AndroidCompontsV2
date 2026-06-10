package com.au.module_androiduiex.styles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/** 从宿主 App assets/fonts 加载字体。 */
@Composable
fun rememberUiExFontFamily(): FontFamily {
    val context = LocalContext.current
    return remember(context) {
        FontFamily(
            Font("fonts/Regular.ttf", context.assets, FontWeight.Normal),
            Font("fonts/Medium.ttf", context.assets, FontWeight.Medium),
            Font("fonts/SemiBold.ttf", context.assets, FontWeight.SemiBold),
        )
    }
}
