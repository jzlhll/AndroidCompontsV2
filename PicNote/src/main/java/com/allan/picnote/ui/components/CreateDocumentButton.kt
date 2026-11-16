package com.allan.picnote.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.allan.picnote.ui.theme.LocalThemePalette

/** 为创建文档操作应用当前 PicNote 主题的专用配色。 */
@Composable
fun CreateDocumentButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = LocalThemePalette.current.createDocument
    Surface(onClick = onClick, modifier = modifier.semantics { role = Role.Button }, enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        color = if (enabled) colors.background else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (enabled) colors.foreground else MaterialTheme.colorScheme.onSurfaceVariant) {
        Text(label, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
    }
}
