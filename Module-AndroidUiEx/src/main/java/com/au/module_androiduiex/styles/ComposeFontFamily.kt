package com.au.module_androiduiex.styles

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Typeface
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.au.module_android.Globals
import com.au.module_androidui.fontutil.getOrCreateTextFontFace
import com.au.module_androidui.widget.FontMode

private lateinit var previewFontAssets: AssetManager

private val fontAssets: AssetManager
    get() = if (Globals.isAppInitialized()) Globals.app.assets else previewFontAssets

@Deprecated("仅适用于preview调试显示")
/** Studio Preview 中注入字体 assets，避免依赖正式 Application。 */
fun bindPreviewFontAssets(assets: AssetManager) {
    previewFontAssets = assets
}

/** Compose 与 View 共用原生字体族，确保声明字重与实际 fallback 请求一致。 */
private class AppFont(val mode: FontMode, override val style: FontStyle) : AndroidFont(
    loadingStrategy = FontLoadingStrategy.Blocking,
    typefaceLoader = AppFontLoader,
    variationSettings = FontVariation.Settings(),
) {
    override val weight = FontWeight(mode.weight)
}

private object AppFontLoader : AndroidFont.TypefaceLoader {
    override fun loadBlocking(context: Context, font: AndroidFont): Typeface {
        val appFont = font as AppFont
        return getOrCreateTextFontFace(
            fontAssets,
            appFont.mode,
            italic = appFont.style == FontStyle.Italic,
        )
    }

    override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface = loadBlocking(context, font)
}

val RegularFontFamily by lazy { FontFamily(AppFont(FontMode.NORMAL, FontStyle.Normal)) }

val MediumFontFamily by lazy { FontFamily(AppFont(FontMode.MID, FontStyle.Normal)) }

val SemiBoldFontFamily by lazy { FontFamily(AppFont(FontMode.BOLD, FontStyle.Normal)) }

val HeavyFontFamily by lazy { FontFamily(AppFont(FontMode.HEAVY, FontStyle.Normal)) }
