package com.allan.mydroid.views.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.allan.mydroid.R
import com.au.module_androiduiex.styles.ComposeColors
import com.au.module_androiduiex.styles.ComposeDimens
import com.au.module_androiduiex.styles.ComposeLineTabLayout
import com.au.module_androiduiex.styles.ComposeLineTabLayoutStyle
import com.au.module_androiduiex.styles.ComposeTypography
import com.au.module_androiduiex.styles.noBackClickable
import kotlinx.coroutines.launch

/** MyDroidAllFragment 页面 UI 状态。 */
data class MyDroidAllUiState(
    /** 当前 IP；null 表示未连接 Wi-Fi / 热点。 */
    val ip: String?,
    /** 网络监控是否已收到首次回调（区分 Uninitialized 与 Disconnected）。 */
    val initialized: Boolean,
)

/**
 * mydroid 首页：Tab(host / client) + Pager。
 * - host 页：IP 提示 + 接收 / 发送 / 聊天 三个功能按钮。
 * - client 页：暂时置空。
 */
@Composable
fun MyDroidAllScreen(
    uiState: MyDroidAllUiState,
    onReceiveFile: () -> Unit,
    onSendFile: () -> Unit,
    onTextChat: () -> Unit,
) {
    val pagerState = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    val tabStyle = ComposeLineTabLayoutStyle(
        height = 46.dp,
        startPadding = 16.dp,
        itemHorizontalPadding = 24.dp,
        indicatorHeight = 2.5.dp,
        selectedTextStyle = ComposeTypography.Font16B,
        unselectedTextStyle = ComposeTypography.Font16,
        distributeEvenly = true,
    )

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        MyDroidAllHeader()
        IpText(ip = uiState.ip, initialized = uiState.initialized)
        ComposeLineTabLayout(
            tabs = listOf(stringResource(R.string.tab_host), stringResource(R.string.tab_client)),
            selectedIndex = pagerState.currentPage,
            style = tabStyle,
            onTabClick = { scope.launch { pagerState.animateScrollToPage(it) } },
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            when (page) {
                0 -> HostPage(
                    onReceiveFile = onReceiveFile,
                    onSendFile = onSendFile,
                    onTextChat = onTextChat,
                )

                1 -> ClientPage()
            }
        }
    }
}

/** 顶部 IP 提示。initialized=false 阶段占位保留高度，避免 UI 跳动。 */
@Composable
private fun IpText(ip: String?, initialized: Boolean) {
    val text = if (!initialized) {
        ""
    } else {
        ip ?: stringResource(R.string.connect_wifi_or_hotspot)
    }
    BasicText(
        text = text,
        style = ComposeTypography.Font16M.copy(textAlign = TextAlign.Center),
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .wrapContentHeight(Alignment.CenterVertically),
    )
}

/** 首页顶部标题栏：仅显示 app_name，无返回按钮。 */
@Composable
private fun MyDroidAllHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = ComposeDimens.ToolbarMarginTop)
            .height(ComposeDimens.ToolbarHeight),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = stringResource(R.string.app_name),
            style = ComposeTypography.Font20M.copy(textAlign = TextAlign.Center),
        )
    }
}

@Composable
private fun HostPage(
    onReceiveFile: () -> Unit,
    onSendFile: () -> Unit,
    onTextChat: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .background(MyDroidComposeColors.NormalBlock, RoundedCornerShape(24.dp)),
        ) {
            LogicButton(
                text = stringResource(R.string.fragment_my_droid_all_file_receive),
                backgroundColor = MyDroidComposeColors.LogicReceiver,
                contentPadding = PaddingValues(top = 21.dp),
                onClick = onReceiveFile,
            )
            LogicButton(
                text = stringResource(R.string.fragment_my_droid_all_file_send),
                backgroundColor = MyDroidComposeColors.LogicSend,
                contentPadding = PaddingValues(top = 16.dp),
                onClick = onSendFile,
            )
            LogicButton(
                text = stringResource(R.string.fragment_my_droid_all_text_chat_room),
                backgroundColor = MyDroidComposeColors.LogicMiddle,
                contentPadding = PaddingValues(top = 16.dp),
                onClick = onTextChat,
            )
            Spacer(modifier = Modifier.height(21.dp))
        }
    }
}

@Composable
private fun LogicButton(
    text: String,
    backgroundColor: androidx.compose.ui.graphics.Color,
    contentPadding: PaddingValues,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp)
            .padding(contentPadding),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .background(backgroundColor, RoundedCornerShape(16.dp))
                .noBackClickable(onClick),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = text,
                style = ComposeTypography.Font20MWhite.copy(textAlign = TextAlign.Center),
            )
        }
    }
}

@Composable
private fun ClientPage() {
    Box(modifier = Modifier.fillMaxSize())
}
