package com.au.module_androiduiex.styles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.au.module_android.Globals
import com.au.module_android.click.acceptClick

/** 无 ripple 背景的点击。 */
@Composable
fun Modifier.noBackClickable(
    paddingTime: Long = Globals.globalPaddingClickTime,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = {
            if (acceptClick(paddingTime)) onClick()
        },
    )
}
