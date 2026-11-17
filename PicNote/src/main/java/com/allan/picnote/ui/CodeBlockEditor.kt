package com.allan.picnote.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.allan.picnote.R
import com.au.module_androiduiex.styles.ComposeTextButton as TextButton

@Composable
fun CodeBlockEditor(
    state: EditorState,
    enabled: Boolean,
    onDescriptionChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var choosingLanguage by rememberSaveable { mutableStateOf(false) }
    val highlighted = rememberHighlightedCode(state.code, state.codeLanguage)
    val transformation = remember(highlighted) {
        VisualTransformation { text ->
            TransformedText(if (text.text == highlighted.text) highlighted else text, OffsetMapping.Identity)
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.code_description), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Box {
                val language = CodeLanguages[state.codeLanguage] ?: state.codeLanguage.ifBlank { stringResource(R.string.plain_text) }
                TextButton(onClick = { choosingLanguage = true }, enabled = enabled) {
                    Text(stringResource(R.string.code_language_value, language))
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                }
                DropdownMenu(expanded = choosingLanguage && enabled, onDismissRequest = { choosingLanguage = false },
                    modifier = Modifier.heightIn(max = 360.dp)) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.plain_text)) },
                        onClick = { onLanguageChange(""); choosingLanguage = false })
                    CodeLanguages.forEach { (value, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { onLanguageChange(value); choosingLanguage = false })
                    }
                }
            }
        }
        OutlinedTextField(
            value = state.body,
            onValueChange = onDescriptionChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            minLines = 3,
        )
        OutlinedTextField(
            value = state.code,
            onValueChange = onCodeChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = 280.dp),
            enabled = enabled,
            label = { Text(stringResource(R.string.code_block)) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            visualTransformation = transformation,
        )
    }
}
