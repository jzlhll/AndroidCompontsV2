package com.au.module_androiduiex.styles

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.au.module_android.Globals
import com.au.module_android.click.acceptClick

/** Compose 点击复用 View.onClick 的全局防抖规则。 */
fun Modifier.onClick(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    paddingTime: Long = Globals.globalPaddingClickTime,
    onClick: () -> Unit,
): Modifier = clickable(enabled = enabled, onClickLabel = onClickLabel, role = role) {
    if (acceptClick(paddingTime)) onClick()
}

/** 无 ripple 背景的点击。 */
@Composable
fun Modifier.noBackClickable(
    paddingTime: Long = Globals.globalPaddingClickTime,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    if (onLongClick != null) {
        return noBackCombinedClickable(
            interactionSource = interactionSource,
            paddingTime = paddingTime,
            onLongClick = onLongClick,
            onClick = onClick,
        )
    }
    return clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = {
            if (acceptClick(paddingTime)) onClick()
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.noBackCombinedClickable(
    interactionSource: MutableInteractionSource,
    paddingTime: Long,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
): Modifier {
    return combinedClickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = {
            if (acceptClick(paddingTime)) onClick()
        },
        onLongClick = onLongClick,
    )
}
