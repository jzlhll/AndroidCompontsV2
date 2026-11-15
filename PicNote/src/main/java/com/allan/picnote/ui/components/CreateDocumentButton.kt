package com.allan.picnote.ui.components

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.allan.picnote.ui.theme.LocalThemePalette
import com.au.module_androiduiex.styles.ComposeButton

/** 为创建文档操作应用当前 PicNote 主题的专用配色。 */
@Composable
fun CreateDocumentButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = LocalThemePalette.current.createDocument
    ComposeButton(onClick = onClick, modifier = modifier, enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = colors.background, contentColor = colors.foreground)) {
        Text(label)
    }
}
