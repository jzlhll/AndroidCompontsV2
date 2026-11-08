package com.au.module_androiduiex.styles

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/** 页面空白区域点击时清除输入焦点并收起键盘，不抢占子控件已处理的点击和拖动。 */
@Composable
fun Modifier.dismissKeyboardOnBlankTap(): Modifier {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    return pointerInput(focusManager, keyboardController) {
        detectTapGestures {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }
}
