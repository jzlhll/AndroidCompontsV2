package com.allan.mydroid.client

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.allan.mydroid.R
import com.au.module_androiduiex.styles.ComposeTypography

/**
 * client 三模式共用的顶部 Header。复刻 host 三页面提示样式：
 * - 第一行：模式标题
 * - 第二行：不退出，不熄屏
 * - 第三行：已连接到 ip:port
 *
 * 注意：沉浸式由 ComposeViewFragment.FullImmersive + statusBarsPadding 处理。
 */
@Composable
fun ConnectToHostHeader(
    titleText: String,
    ip: String,
    httpPort: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(
            text = titleText,
            style = ComposeTypography.Font20M.copy(textAlign = TextAlign.Center),
        )
        BasicText(
            text = stringResource(R.string.not_close_window).format(""),
            style = ComposeTypography.Font14sp.copy(textAlign = TextAlign.Center),
            modifier = Modifier.padding(top = 4.dp),
        )
        BasicText(
            text = stringResource(R.string.lan_access_fmt).format(ip, httpPort.toString()),
            style = ComposeTypography.Font14sp.copy(textAlign = TextAlign.Center),
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** 顶部红色断连提示条。 */
@Composable
fun DisconnectedTip(text: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFFFCDD2))
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = ComposeTypography.Font14sp.copy(color = Color(0xFFC62828), textAlign = TextAlign.Center),
        )
    }
}
