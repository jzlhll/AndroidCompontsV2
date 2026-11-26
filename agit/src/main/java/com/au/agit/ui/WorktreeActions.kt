package com.au.agit.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.au.agit.R
import com.au.agit.model.FileDiscardAction
import com.au.agit.model.FileDiscardPlan
import com.au.agit.ui.components.AGitIconButton
import com.au.agit.ui.components.AGitTextButton

@Composable
fun FileDiscardMenu(paths: List<String>, enabled: Boolean, onDiscardFile: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        AGitIconButton(onClick = { expanded = true }, enabled = enabled) {
            Icon(Icons.Default.MoreVert, stringResource(R.string.file_actions))
        }
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            paths.forEach { path ->
                DropdownMenuItem(
                    text = { Text(if (paths.size == 1) stringResource(R.string.discard_file_changes) else stringResource(R.string.discard_path_changes, path)) },
                    onClick = {
                        expanded = false
                        onDiscardFile(path)
                    },
                )
            }
        }
    }
}

@Composable
fun FileDiscardDialog(plan: FileDiscardPlan, confirmEnabled: Boolean, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val title = when (plan.action) {
        FileDiscardAction.DeleteUntracked -> R.string.delete_untracked_file
        FileDiscardAction.RestoreFromHead -> R.string.restore_tracked_file
        FileDiscardAction.RemoveStagedAddition -> R.string.remove_staged_addition
    }
    val body = when (plan.action) {
        FileDiscardAction.DeleteUntracked -> R.string.delete_untracked_body
        FileDiscardAction.RestoreFromHead -> R.string.restore_tracked_body
        FileDiscardAction.RemoveStagedAddition -> R.string.remove_staged_addition_body
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(body, plan.path)) },
        confirmButton = { AGitTextButton(onClick = onConfirm, enabled = confirmEnabled) { Text(stringResource(title)) } },
        dismissButton = { AGitTextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}
