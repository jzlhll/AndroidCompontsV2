package com.au.module_androiduiex.styles

import android.content.res.AssetManager
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.au.module_android.Globals

private const val REGULAR_TTF = "fonts/normal.ttf"
private const val MEDIUM_TTF = "fonts/medium.ttf"
private const val BOLD_TTF = "fonts/bold.ttf"

private lateinit var previewFontAssets: AssetManager

private val fontAssets: AssetManager
    get() = if (Globals.isAppInitialized()) Globals.app.assets else previewFontAssets

@Deprecated("仅适用于preview调试显示")
/** Studio Preview 中注入字体 assets，避免依赖正式 Application。 */
fun bindPreviewFontAssets(assets: AssetManager) {
    previewFontAssets = assets
}

/** 从宿主 App assets/fonts 加载 Lufga 字体。 */
val RegularFontFamily by lazy { FontFamily(Font(REGULAR_TTF, fontAssets)) }

/** 从宿主 App assets/fonts 加载 Lufga Medium 字体。 */
val MediumFontFamily by lazy { FontFamily(Font(MEDIUM_TTF, fontAssets)) }

/** 从宿主 App assets/fonts 加载 Lufga SemiBold 字体。 */
val BoldFontFamily by lazy { FontFamily(Font(BOLD_TTF, fontAssets)) }
