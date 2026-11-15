package com.au.module_androiduiex.styles

import androidx.compose.foundation.layout.*
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp

@Composable
fun ComposeButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(), content: @Composable RowScope.() -> Unit) {
    androidx.compose.material3.Button(onClick, modifier.heightIn(min = ButtonDefaults.MinHeight + 6.dp), enabled = enabled,
        colors = colors, contentPadding = expandedComposeButtonPadding(ButtonDefaults.ContentPadding), content = content)
}

@Composable
fun ComposeOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    androidx.compose.material3.OutlinedButton(onClick, modifier.heightIn(min = ButtonDefaults.MinHeight + 6.dp), enabled = enabled,
        contentPadding = expandedComposeButtonPadding(ButtonDefaults.ContentPadding), content = content)
}

@Composable
fun ComposeTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    contentPadding: PaddingValues = expandedComposeButtonPadding(ButtonDefaults.TextButtonContentPadding), content: @Composable RowScope.() -> Unit) {
    androidx.compose.material3.TextButton(onClick, modifier.heightIn(min = ButtonDefaults.MinHeight + 6.dp), enabled = enabled,
        contentPadding = contentPadding, content = content)
}

@Composable
fun ComposeIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    androidx.compose.material3.IconButton(onClick, modifier.heightIn(min = 54.dp), enabled = enabled, content = content)
}

@Composable
fun expandedComposeButtonPadding(padding: PaddingValues): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(start = padding.calculateStartPadding(direction), top = padding.calculateTopPadding() + 3.dp,
        end = padding.calculateEndPadding(direction), bottom = padding.calculateBottomPadding() + 3.dp)
}
