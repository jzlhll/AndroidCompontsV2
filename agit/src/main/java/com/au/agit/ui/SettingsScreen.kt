package com.au.agit.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.au.agit.R
import com.au.agit.ui.theme.AGitTheme
import com.au.module_android.appLocaleLabel
import java.util.Locale

@Composable
fun SettingsScreen(onBack: () -> Unit, onLogs: () -> Unit, onLanguage: () -> Unit, modifier: Modifier = Modifier) {
    var languageTag by remember { mutableStateOf(AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag().orEmpty()) }
    LifecycleResumeEffect(Unit) {
        languageTag = AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag().orEmpty()
        onPauseOrDispose { }
    }
    val language = if (languageTag.isEmpty()) null else Locale.forLanguageTag(languageTag)
    PageScaffold(stringResource(R.string.settings), onBack, modifier) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.language)) },
                supportingContent = { Text(language?.let(::appLocaleLabel) ?: stringResource(R.string.follow_system)) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                modifier = Modifier.clickable { onLanguage() },
            )
            ListItem(headlineContent = { Text(stringResource(R.string.logs_title)) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }, modifier = Modifier.clickable(onClick = onLogs))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsPreview() { AGitTheme { SettingsScreen({}, {}, {}) } }
