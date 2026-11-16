package com.allan.picnote.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.allan.picnote.R
import com.au.module_android.appLocaleLabel
import com.au.module_android.supportedAppLocales
import com.au.module_androiduiex.picker.AppLanguagePicker
import com.au.module_androiduiex.styles.ComposeTheme
import com.au.module_androiduiex.styles.ComposeTypography
import com.au.module_androiduiex.styles.ComposeIconButton as IconButton
import java.util.Locale

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLanguage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var languageTag by remember { mutableStateOf(AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag().orEmpty()) }
    LifecycleResumeEffect(Unit) {
        languageTag = AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag().orEmpty()
        onPauseOrDispose { }
    }
    val language = if (languageTag.isEmpty()) null else Locale.forLanguageTag(languageTag)
    SettingsScaffold(stringResource(R.string.settings), onBack, modifier) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.language), style = ComposeTypography.Font16, color = MaterialTheme.colorScheme.onSurface) },
                    supportingContent = {
                        Text(language?.let(::appLocaleLabel) ?: stringResource(R.string.follow_system),
                            style = ComposeTypography.Font14sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                    modifier = Modifier.clickable { onLanguage() },
                )
            }
        }
    }
}

@Composable
fun AppearanceScreen(
    selected: ComposeTheme,
    onSelect: (ComposeTheme) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    saving: Boolean = false,
) {
    SettingsScaffold(stringResource(R.string.appearance), onBack, modifier) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).selectableGroup(), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(ComposeTheme.entries, key = { it.name }) { theme ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .selectable(selected = selected == theme, enabled = !saving, role = Role.RadioButton, onClick = { onSelect(theme) })
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected == theme, onClick = null, enabled = !saving)
                    Text(if (theme == ComposeTheme.FollowSystem) stringResource(R.string.follow_system) else theme.name,
                        style = ComposeTypography.Font16, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
fun LanguageScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val locales = remember(context) { supportedAppLocales(context, R.xml.locales_config) }
    SettingsScaffold(stringResource(R.string.language), onBack, modifier) { padding ->
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                expandedHeight = 64.dp,
                title = { Text(title, style = ComposeTypography.Font20M, color = MaterialTheme.colorScheme.onSurface) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
        content = content,
    )
}
