package com.au.agit.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.os.LocaleListCompat
import com.au.agit.R
import com.au.agit.ui.theme.AGitTheme
import com.au.module_android.supportedAppLocales
import com.au.module_androiduiex.picker.AppLanguagePicker
import com.au.module_androiduiex.preview.AppPreview

@Composable
fun LanguageScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val locales = remember(context) { supportedAppLocales(context, R.xml.locales_config) }
    PageScaffold(stringResource(R.string.language), onBack, modifier) { padding ->
        AppLanguagePicker(
            locales = locales,
            selectedLanguageTag = AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag().orEmpty(),
            followSystemLabel = stringResource(R.string.follow_system),
            onSelect = { tag ->
                onBack()
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
            },
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LanguagePreview() { AppPreview { AGitTheme { LanguageScreen({}) } } }
