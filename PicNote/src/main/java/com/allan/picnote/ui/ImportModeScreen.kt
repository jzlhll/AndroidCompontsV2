package com.allan.picnote.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.allan.picnote.R
import com.allan.picnote.data.ImportMode
import com.allan.picnote.ui.components.ImportModeIcon
import com.au.module_androiduiex.landscape.SecondaryPageScaffold
import com.au.module_androiduiex.styles.ComposeTypography

@StringRes
fun importModeTitle(mode: ImportMode): Int = when (mode) {
    ImportMode.AddOnly -> R.string.import_mode_add_only
    ImportMode.DeleteSelected -> R.string.import_mode_delete_selected
    ImportMode.DeleteBatch -> R.string.import_mode_delete_batch
}

@Composable
fun ImportModeScreen(
    selected: ImportMode,
    onSelect: (ImportMode) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SecondaryPageScaffold(stringResource(R.string.import_mode), onBack, stringResource(R.string.back), modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).selectableGroup().padding(vertical = 8.dp)) {
            ImportMode.entries.forEach { mode ->
                Row(
                    Modifier.fillMaxWidth()
                        .selectable(selected = selected == mode, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(mode) })
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top,
                ) {
                    RadioButton(selected = selected == mode, onClick = null, enabled = enabled)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            ImportModeIcon(mode, enabled = enabled, tint = MaterialTheme.colorScheme.onSurface)
                            Text(stringResource(importModeTitle(mode)), style = ComposeTypography.Font16M,
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                        Text(stringResource(when (mode) {
                            ImportMode.AddOnly -> R.string.import_mode_add_only_description
                            ImportMode.DeleteSelected -> R.string.import_mode_delete_selected_description
                            ImportMode.DeleteBatch -> R.string.import_mode_delete_batch_description
                        }), style = ComposeTypography.Font14sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
