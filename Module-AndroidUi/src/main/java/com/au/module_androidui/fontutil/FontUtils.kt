package com.au.module_androidui.fontutil

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.graphics.fonts.FontStyle
import android.util.AttributeSet
import android.widget.TextView
import androidx.core.content.withStyledAttributes
import com.au.module_android.utils.ignoreError
import com.au.module_androidui.BuildConfig
import com.au.module_androidui.R
import com.au.module_androidui.widget.FontMode
import com.au.module_androidui.widget.TextViewCheckMode

private val fontFaceMap by lazy { hashMapOf<String, Typeface?>() }

private data class TextFontFamilyKey(
    val assets: AssetManager,
    val isNumber: Boolean,
    val fontName: String,
)

private data class TextFontKey(
    val family: TextFontFamilyKey,
    val mode: FontMode,
    val italic: Boolean,
)

private val textFontFamilyMap = hashMapOf<TextFontFamilyKey, FontFamily?>()
private val textFontFaceMap = hashMapOf<TextFontKey, Typeface>()

fun getOrCreateFontFace(context: Context, assetsPath: String?) : Typeface? {
    if(assetsPath.isNullOrEmpty()) return null
    if (fontFaceMap.contains(assetsPath)) {
        return fontFaceMap[assetsPath]
    }
    val newTypeFace = ignoreError { Typeface.createFromAsset(context.assets, assetsPath) }
    fontFaceMap[assetsPath] = newTypeFace
    return newTypeFace
}

/** 文字字体统一保留字重和斜体请求，缺失字形由系统 fallback 链处理。 */
fun getOrCreateTextFontFace(
    assets: AssetManager,
    mode: FontMode,
    isNumber: Boolean = false,
    fontName: String = "",
    italic: Boolean = false,
): Typeface = synchronized(textFontFaceMap) {
    val familyKey = TextFontFamilyKey(assets, isNumber, fontName)
    val key = TextFontKey(familyKey, mode, italic)
    textFontFaceMap.getOrPut(key) {
        if (!textFontFamilyMap.containsKey(familyKey)) {
            val fonts = if (fontName.isNotEmpty()) {
                listOfNotNull(ignoreError { Font.Builder(assets, "fonts/$fontName").build() })
            } else {
                FontMode.entries.mapNotNull { fontMode ->
                    val path = if (isNumber) {
                        when (fontMode) {
                            FontMode.NORMAL -> BuildConfig.FONT_NUMBER_PATH
                            FontMode.MID -> BuildConfig.FONT_NUMBER_MEDIUM_PATH
                            FontMode.BOLD -> BuildConfig.FONT_NUMBER_BOLD_PATH
                            FontMode.HEAVY -> BuildConfig.FONT_NUMBER_HEAVY_PATH
                        }
                    } else {
                        when (fontMode) {
                            FontMode.NORMAL -> BuildConfig.FONT_NORMAL_PATH
                            FontMode.MID -> BuildConfig.FONT_MEDIUM_PATH
                            FontMode.BOLD -> BuildConfig.FONT_BOLD_PATH
                            FontMode.HEAVY -> BuildConfig.FONT_HEAVY_PATH
                        }
                    }
                    if (path.isEmpty()) null else ignoreError {
                        Font.Builder(assets, path).setWeight(fontMode.weight).build()
                    }
                }
            }
            textFontFamilyMap[familyKey] = fonts.firstOrNull()?.let { firstFont ->
                val builder = FontFamily.Builder(firstFont)
                fonts.drop(1).forEach { builder.addFont(it) }
                builder.build()
            }
        }
        val family = textFontFamilyMap[familyKey]
        if (family == null) {
            Typeface.create(Typeface.SANS_SERIF, mode.weight, italic)
        } else {
            val slant = if (italic) FontStyle.FONT_SLANT_ITALIC else FontStyle.FONT_SLANT_UPRIGHT
            Typeface.CustomFallbackBuilder(family)
                .setSystemFallback("sans-serif")
                .setStyle(FontStyle(mode.weight, slant))
                .build()
        }
    }
}

/** customFontMode 优先于平台样式，已有 bold 默认使用 600。 */
fun TextView.checkBoldAndSetFont(
    cxt: Context,
    attrs: AttributeSet?,
    defStyleAttr: Int = 0,
): TextViewCheckMode {
    var isNumber = false
    var fontMode = when {
        (typeface?.weight ?: 400) >= FontMode.BOLD.weight -> FontMode.BOLD
        (typeface?.weight ?: 400) >= FontMode.MID.weight -> FontMode.MID
        else -> FontMode.NORMAL
    }
    var fontName = ""

    cxt.withStyledAttributes(attrs, R.styleable.CustomFontText, defStyleAttr) {
        fontName = getString(R.styleable.CustomFontText_customFontName) ?: ""
        val requestedMode = getString(R.styleable.CustomFontText_customFontMode)
        fontMode = FontMode.entries.firstOrNull { it.mode == requestedMode } ?: fontMode
        isNumber = getBoolean(R.styleable.CustomFontText_customFontIsNum, false)
    }

    setFontFromAsset(context, fontMode, isNumber, fontName)
    return TextViewCheckMode(fontMode, isNumber, fontName)
}

fun TextView.setFontFromAsset(context: Context, mode: FontMode, isNumber: Boolean, fontName: String) {
    val tf = getOrCreateTextFontFace(context.assets, mode, isNumber, fontName, typeface?.isItalic == true)
    paint.isFakeBoldText = false
    paint.textSkewX = 0f
    if (tf != typeface) {
        typeface = tf
    }
}
