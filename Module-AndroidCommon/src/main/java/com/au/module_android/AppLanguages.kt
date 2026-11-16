package com.au.module_android

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.annotation.XmlRes
import com.au.module_android.log.logEx
import org.xmlpull.v1.XmlPullParser
import java.util.Locale

/** 语言选择器与 Manifest 使用同一份 localeConfig，名称按各语言自身的写法显示。 */
fun supportedAppLocales(context: Context, @XmlRes localeConfig: Int): List<Locale> =
    context.resources.getXml(localeConfig).use { parser ->
        buildList {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                    val tag = parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name")
                    if (!tag.isNullOrEmpty()) add(Locale.forLanguageTag(tag))
                }
            }
        }
    }

/**
 * 语言名称按标签固定：中文区分简繁，其他语言取该语言自身的写法。
 * 不依赖系统 ICU 的 getDisplayName，避免不同系统返回“中文 (中國台灣)”“中文 (简体中文)”这类带括注的差异。
 */
fun appLocaleLabel(locale: Locale): String {
    val tag = locale.toLanguageTag()
    return when {
        tag == "zh" || tag.startsWith("zh-") ->
            if (tag.contains("Hant") || tag.endsWith("TW") || tag.endsWith("HK") || tag.endsWith("MO")) "繁體中文" else "简体中文"
        else -> locale.getDisplayLanguage(locale).ifBlank { tag }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun Context.openApplicationLanguageSettings(): Boolean = try {
    startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", packageName, null)))
    true
} catch (error: ActivityNotFoundException) {
    logEx(throwable = error) { "Open language settings failed" }
    false
} catch (error: SecurityException) {
    logEx(throwable = error) { "Open language settings denied" }
    false
}
