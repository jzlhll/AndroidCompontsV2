package com.allan.picnote.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.allan.picnote.R
import com.allan.picnote.data.entity.DocumentContent
import com.au.module_androiduiex.styles.ComposeTextButton as TextButton

@Composable
fun MergeNodesDialog(
    content: DocumentContent,
    nodeIds: List<String>,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val ids = nodeIds.toSet()
    val selected = content.nodes.withIndex().filter { it.value.node.id in ids }
    val valid = ids.size >= 2 && ids.size == nodeIds.size && selected.size == ids.size &&
        selected.all { it.value.node.kind == "body" }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.merge_nodes)) },
        text = {
            Text(if (valid) {
                val numbers = selected.joinToString(", ") { (it.index + 1).toString().padStart(2, '0') }
                val imageCount = selected.sumOf { it.value.images.size }
                val firstNumber = (selected.first().index + 1).toString().padStart(2, '0')
                stringResource(R.string.merge_nodes_message, numbers, imageCount, firstNumber)
            } else stringResource(R.string.merge_nodes_changed),
                modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()))
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = enabled && valid) { Text(stringResource(R.string.merge)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
