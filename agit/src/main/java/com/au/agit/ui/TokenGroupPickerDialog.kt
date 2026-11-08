package com.au.agit.ui

import com.au.agit.ui.components.AGitOutlinedButton as OutlinedButton
import com.au.agit.ui.components.AGitTextButton as TextButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.au.agit.R
import com.au.agit.model.AppTheme
import com.au.agit.model.TokenGroup
import com.au.agit.ui.theme.AGitTheme
import com.au.module_androiduiex.preview.AppPreview

@Composable
fun TokenGroupPickerDialog(
    groups: List<TokenGroup>,
    onDismiss: () -> Unit,
    onGroup: (String) -> Unit,
    onAddGroup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(onDismissRequest = onDismiss, modifier = modifier,
        title = { Text(stringResource(R.string.select_group)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(groups, key = { it.id }) { group ->
                    TextButton(onClick = { onGroup(group.id) }, modifier = Modifier.fillMaxWidth()) { Text(group.name) }
                }
                item {
                    OutlinedButton(onClick = onAddGroup, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.add_token))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Preview(showBackground = true, locale = "en")
@Preview(showBackground = true, locale = "zh-rCN")
@Composable
private fun TokenGroupPickerPreview() {
    AppPreview {
        AGitTheme(AppTheme.Black) {
            TokenGroupPickerDialog(listOf(TokenGroup("preview", "Personal", "")), {}, {}, {})
        }
    }
}
