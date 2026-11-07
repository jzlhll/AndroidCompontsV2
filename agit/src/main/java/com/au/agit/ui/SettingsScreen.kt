package com.au.agit.ui

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.au.agit.R
import com.au.agit.ui.theme.AGitTheme

@Composable
fun SettingsScreen(onBack: () -> Unit, onLogs: () -> Unit, modifier: Modifier = Modifier) {
    PageScaffold(stringResource(R.string.settings), onBack, modifier) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ListItem(headlineContent = { Text(stringResource(R.string.logs_title)) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }, modifier = Modifier.clickable(onClick = onLogs))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsPreview() { AGitTheme { SettingsScreen({}, {}) } }
