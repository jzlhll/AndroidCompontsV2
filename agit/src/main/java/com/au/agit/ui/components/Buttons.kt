package com.au.agit.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.au.module_androiduiex.styles.ComposeButton
import com.au.module_androiduiex.styles.ComposeOutlinedButton
import com.au.module_androiduiex.styles.ComposeTextButton
import com.au.module_androiduiex.styles.ComposeIconButton
import com.au.module_androiduiex.styles.expandedComposeButtonPadding

@Composable
fun AGitButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(), content: @Composable RowScope.() -> Unit) {
    ComposeButton(onClick, modifier, enabled, colors, content)
}

@Composable
fun AGitOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    ComposeOutlinedButton(onClick, modifier, enabled, content)
}

@Composable
fun AGitTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    contentPadding: PaddingValues = expandedComposeButtonPadding(ButtonDefaults.TextButtonContentPadding), content: @Composable RowScope.() -> Unit) {
    ComposeTextButton(onClick, modifier, enabled, contentPadding, content)
}

@Composable
fun AGitIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    ComposeIconButton(onClick, modifier, enabled, content)
}
