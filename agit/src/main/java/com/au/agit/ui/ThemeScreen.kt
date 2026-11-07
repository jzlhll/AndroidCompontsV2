package com.au.agit.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.au.agit.R
import com.au.agit.model.AppTheme
import com.au.agit.model.ChangeType
import com.au.agit.ui.theme.AGitTheme
import com.au.agit.ui.theme.themePalette

@Composable
fun ThemeScreen(selected: AppTheme, onSelect: (AppTheme) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    PageScaffold(stringResource(R.string.appearance), onBack, modifier) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(AppTheme.entries, key = { it.name }) { theme ->
                val palette = themePalette(theme)
                val colors = palette.scheme
                OutlinedCard(onClick = { onSelect(theme) }, colors = CardDefaults.outlinedCardColors(containerColor = colors.surface, contentColor = colors.onSurface), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(when (theme) {
                                AppTheme.Black -> R.string.theme_black
                                AppTheme.White -> R.string.theme_white
                                AppTheme.Dracula -> R.string.theme_dracula
                                AppTheme.Sage -> R.string.theme_sage
                            }), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            if (selected == theme) Icon(Icons.Default.Check, stringResource(R.string.selected), tint = colors.primary)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ChangeType.entries.forEach { type ->
                                val status = palette.statuses.getValue(type)
                                Surface(color = status.background, contentColor = status.foreground, shape = MaterialTheme.shapes.small) {
                                    Text(type.marker, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
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
private fun ThemePreview() { AGitTheme { ThemeScreen(AppTheme.White, {}, {}) } }
