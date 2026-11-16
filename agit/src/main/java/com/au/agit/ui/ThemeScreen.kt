package com.au.agit.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.au.agit.R
import com.au.agit.model.AppTheme
import com.au.agit.model.ChangeType
import com.au.agit.ui.theme.AGitTheme
import com.au.agit.ui.theme.themePalette
import com.au.module_androiduiex.preview.AppPreview
import com.au.module_androiduiex.styles.ComposeTypography

@Composable
fun ThemeScreen(selected: AppTheme, onSelect: (AppTheme) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val systemDark = isSystemInDarkTheme()
    PageScaffold(stringResource(R.string.appearance), onBack, modifier) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).selectableGroup(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(AppTheme.entries, key = { it.name }, contentType = { "theme" }) { theme ->
                val palette = themePalette(theme, systemDark)
                val colors = palette.scheme
                OutlinedCard(colors = CardDefaults.outlinedCardColors(containerColor = colors.surface, contentColor = colors.onSurface),
                    modifier = Modifier.fillMaxWidth().selectable(selected = selected == theme, role = Role.RadioButton, onClick = { onSelect(theme) })) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selected == theme, onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = colors.primary, unselectedColor = colors.onSurfaceVariant))
                            Text(stringResource(when (theme) {
                                AppTheme.FollowSystem -> R.string.follow_system
                                AppTheme.Black -> R.string.theme_black
                                AppTheme.White -> R.string.theme_white
                                AppTheme.Dracula -> R.string.theme_dracula
                                AppTheme.Sage -> R.string.theme_sage
                                AppTheme.Alucard -> R.string.theme_alucard
                            }), style = ComposeTypography.Font16M, color = colors.onSurface, modifier = Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ChangeType.entries.forEach { type ->
                                val status = palette.statuses.getValue(type)
                                Surface(color = status.background, contentColor = status.foreground, shape = MaterialTheme.shapes.small) {
                                    Text(type.marker, fontFamily = FontFamily.Monospace, style = ComposeTypography.Font12M, color = status.foreground, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ThemePreview() { AppPreview { AGitTheme { ThemeScreen(AppTheme.White, {}, {}) } } }
