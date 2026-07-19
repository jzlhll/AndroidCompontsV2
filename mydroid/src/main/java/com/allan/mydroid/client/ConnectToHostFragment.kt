package com.allan.mydroid.client

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.au.module_androiduiex.styles.ComposeTypography
import com.au.module_androiduiex.ui.ComposeViewFragment

/**
 * client 端点击已发现 host 后进入的页面。本期仅居中显示点击的完整 IP,便于用户确认。
 * 后续在此填充连接 host 的具体功能。
 *
 * 入参: [android.os.Bundle] 中带 `ip`(String) 与 `port`(Int)。
 */
class ConnectToHostFragment : ComposeViewFragment() {
    private val ip by lazy { arguments?.getString("ip") }
    @Suppress("unused")
    private val port by lazy { arguments?.getInt("port") ?: 0 }

    @Composable
    override fun ScreenContent() {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = ip ?: "",
                style = ComposeTypography.Font20M.copy(textAlign = TextAlign.Center),
            )
        }
    }
}