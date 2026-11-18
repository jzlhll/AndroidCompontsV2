package com.allan.picnote.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
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
import com.allan.picnote.data.CodeWrapSettings
import com.au.module_androiduiex.landscape.SecondaryPageScaffold
import com.au.module_android.appLocaleLabel
import com.au.module_android.supportedAppLocales
import com.au.module_androiduiex.picker.AppLanguagePicker
import com.au.module_androiduiex.styles.ComposeTheme
import com.au.module_androiduiex.styles.ComposeTypography
import java.util.Locale

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLanguage: () -> Unit,
    onAdvanced: () -> Unit,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
) {
    var languageTag by remember { mutableStateOf(AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag().orEmpty()) }
    LifecycleResumeEffect(Unit) {
        languageTag = AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag().orEmpty()
        onPauseOrDispose { }
    }
    val language = if (languageTag.isEmpty()) null else Locale.forLanguageTag(languageTag)
    SecondaryPageScaffold(stringResource(R.string.settings), onBack, stringResource(R.string.back), modifier, loading = busy) {
        LazyColumn(Modifier.fillMaxSize()) {
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
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.advanced), style = ComposeTypography.Font16, color = MaterialTheme.colorScheme.onSurface) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                    modifier = Modifier.clickable(onClick = onAdvanced),
                )
            }
            item {
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.clear_cache), style = ComposeTypography.Font16,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (busy) 0.38f else 1f))
                    },
                    modifier = Modifier.clickable(enabled = !busy, onClick = onClearCache),
                )
            }
        }
    }
}

@Composable
fun AdvancedSettingsScreen(
    settings: CodeWrapSettings?,
    onToggleNodeCardWrap: () -> Unit,
    onToggleDocumentWrap: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = settings != null
    SecondaryPageScaffold(stringResource(R.string.advanced), onBack, stringResource(R.string.back), modifier) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                CodeWrapSettingRow(stringResource(R.string.wrap_code_node_card), settings?.nodeCard == true, enabled, onToggleNodeCardWrap)
            }
            item {
                CodeWrapSettingRow(stringResource(R.string.wrap_document_code), settings?.document == true, enabled, onToggleDocumentWrap)
            }
        }
    }
}

@Composable
private fun CodeWrapSettingRow(label: String, checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch,
                interactionSource = interactionSource, indication = null, onValueChange = { onToggle() })
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = ComposeTypography.Font16,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled, interactionSource = interactionSource)
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
    SecondaryPageScaffold(stringResource(R.string.appearance), onBack, stringResource(R.string.back), modifier) {
        LazyColumn(Modifier.fillMaxSize().selectableGroup(), contentPadding = PaddingValues(vertical = 8.dp)) {
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
    SecondaryPageScaffold(stringResource(R.string.language), onBack, stringResource(R.string.back), modifier) {
        AppLanguagePicker(
            locales = locales,
            selectedLanguageTag = AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag().orEmpty(),
            followSystemLabel = stringResource(R.string.follow_system),
            onSelect = { tag ->
                onBack()
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
