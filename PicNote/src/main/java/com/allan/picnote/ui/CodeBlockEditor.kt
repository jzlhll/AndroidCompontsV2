package com.allan.picnote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
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
    wrapCode: Boolean,
    languageOrder: List<String>,
    onDescriptionChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var choosingLanguage by rememberSaveable { mutableStateOf(false) }
    val highlighted = rememberHighlightedCode(state.code, state.codeLanguage, editing = true)
    val transformation = remember(highlighted) {
        VisualTransformation { text ->
            TransformedText(if (text.text == highlighted.text) highlighted else text, OffsetMapping.Identity)
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.rawCodeMarkdown) {
            Text(stringResource(R.string.code_markdown_source_hint), style = MaterialTheme.typography.bodySmall)
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.code_description), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Box {
                    val language = codeLanguageLabel(state.codeLanguage)
                    TextButton(onClick = { choosingLanguage = true }, enabled = enabled) {
                        Text(stringResource(R.string.code_language_value, language))
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = choosingLanguage && enabled, onDismissRequest = { choosingLanguage = false },
                        modifier = Modifier.heightIn(max = 360.dp)) {
                        languageOrder.forEach { value ->
                            DropdownMenuItem(text = { Text(codeLanguageLabel(value)) },
                                onClick = { onLanguageChange(value); choosingLanguage = false })
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
        }
        val label = stringResource(if (state.rawCodeMarkdown) R.string.code_markdown_source else R.string.code_block)
        Text(label, style = MaterialTheme.typography.titleSmall)
        val horizontalScroll = rememberScrollState()
        val shape = RoundedCornerShape(CodeCornerRadius)
        val colors = MaterialTheme.colorScheme
        BoxWithConstraints(Modifier.fillMaxWidth().clip(shape).background(colors.surfaceVariant)
            .border(1.dp, colors.outline, shape)) {
            val fieldWidth = maxWidth
            Box(if (wrapCode) Modifier else Modifier.horizontalScroll(horizontalScroll, enabled = enabled)) {
                BasicTextField(
                    value = state.code,
                    onValueChange = onCodeChange,
                    modifier = (if (wrapCode) Modifier.fillMaxWidth() else Modifier.widthIn(min = fieldWidth))
                        .heightIn(min = 160.dp, max = 280.dp).padding(CodeContentPadding),
                    enabled = enabled,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace, fontSize = CodeTextSize, color = colors.onSurfaceVariant,
                    ),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None),
                    visualTransformation = transformation,
                )
            }
        }
    }
}
