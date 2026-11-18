package com.allan.picnote.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.allan.picnote.R
import com.allan.picnote.ui.theme.LocalThemePalette

private val DrawerActionVerticalPadding = 6.dp

/** 为创建文档操作应用当前 PicNote 主题的专用配色。 */
@Composable
fun CreateDocumentButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    DocumentDrawerButton(onClick, modifier, enabled) {
        Text(
            label,
            Modifier.padding(horizontal = 12.dp, vertical = DrawerActionVerticalPadding),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
fun BluetoothReceiveButton(
    newDocumentLabel: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    // 使用新建文案的实际行高，字体缩放及不同语言下也能保持轮廓等高。
    val measuredLabel = rememberTextMeasurer().measure(
        newDocumentLabel,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        softWrap = false,
    )
    val lineHeight = with(LocalDensity.current) { measuredLabel.size.height.toDp() }
    DocumentDrawerButton(onClick, modifier, enabled) {
        Box(
            Modifier.width(36.dp).padding(vertical = DrawerActionVerticalPadding).height(lineHeight),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_bluetooth_receive), contentDescription, Modifier.size(20.dp))
        }
    }
}

@Composable
private fun DocumentDrawerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = LocalThemePalette.current.createDocument
    Surface(onClick = onClick, modifier = modifier.semantics { role = Role.Button }, enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        color = if (enabled) colors.background else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (enabled) colors.foreground else MaterialTheme.colorScheme.onSurfaceVariant,
        content = content)
}
