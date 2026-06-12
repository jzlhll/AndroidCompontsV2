package com.au.module_androiduiex.styles

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.au.module_android.Globals

private const val REGULAR_TTF = "fonts/Regular.ttf"
private const val MEDIUM_TTF = "fonts/Medium.ttf"
private const val SEMIBOLD_TTF = "fonts/SemiBold.ttf"

/** 从宿主 App assets/fonts 加载 Regular 字体。 */
val RegularFontFamily by lazy { FontFamily(Font(REGULAR_TTF, Globals.app.assets)) }

/** 从宿主 App assets/fonts 加载 Medium 字体。 */
val MediumFontFamily by lazy { FontFamily(Font(MEDIUM_TTF, Globals.app.assets)) }

/** 从宿主 App assets/fonts 加载 SemiBold 字体。 */
val SemiBoldFontFamily by lazy { FontFamily(Font(SEMIBOLD_TTF, Globals.app.assets)) }
