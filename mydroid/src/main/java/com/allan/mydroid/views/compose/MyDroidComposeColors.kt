package com.allan.mydroid.views.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.allan.mydroid.R

/**
 * mydroid 模块自定义 Compose 颜色。参考 [com.au.module_androiduiex.styles.ComposeColors] 的做法。
 */
object MyDroidComposeColors {
    val LogicReceiver @Composable @ReadOnlyComposable get() = colorResource(R.color.logic_receiver)
    val LogicSend @Composable @ReadOnlyComposable get() = colorResource(R.color.logic_send)
    val LogicMiddle @Composable @ReadOnlyComposable get() = colorResource(R.color.logic_middle)

    val NormalBlock @Composable @ReadOnlyComposable get() = colorResource(com.au.module_androidcolor.R.color.color_normal_block)

    /** 三个功能按钮的文字颜色。 */
    val LogicBtnText = Color.White
}
