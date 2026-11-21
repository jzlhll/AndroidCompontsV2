package com.allan.mydroid.views.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.allan.mydroid.R
import com.allan.mydroid.bt.DiscoveredHost
import com.allan.mydroid.client.TransferTheme
import com.au.module_android.log.logDebug
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
 * - host 页：IP 提示和主机功能入口。
 * - client 页：BLE 雷达扫描 + 8 槽位 IP 矩形。
 */
@Composable
fun MyDroidAllScreen(
    uiState: MyDroidAllUiState,
    onReceiveFile: () -> Unit,
    onSendFile: () -> Unit,
    onTextChat: () -> Unit,
    onInstallApk: () -> Unit,
    discoveredHosts: List<DiscoveredHost>,
    scanning: Boolean,
    localIp: String?,
    onStartSearch: () -> Unit,
    onIpClick: (DiscoveredHost) -> Unit,
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
        modifier = Modifier
            .fillMaxSize()
            .background(ComposeColors.BackgroundBg)
            .navigationBarsPadding(),
    ) {
        MyDroidAllHeader()
        IpText(ip = uiState.ip, initialized = uiState.initialized)
        Spacer(modifier = Modifier.height(18.dp))
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
                    onInstallApk = onInstallApk,
                )

                1 -> ClientPage(
                    discoveredHosts = discoveredHosts,
                    scanning = scanning,
                    localIp = localIp,
                    onStartSearch = onStartSearch,
                    onIpClick = onIpClick,
                )
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
    Text(
        text = text,
        style = ComposeTypography.Font16M,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 24.dp)
            .padding(horizontal = 16.dp)
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
        Text(
            text = stringResource(R.string.app_name),
            style = ComposeTypography.Font20M,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HostPage(
    onReceiveFile: () -> Unit,
    onSendFile: () -> Unit,
    onTextChat: () -> Unit,
    onInstallApk: () -> Unit,
) {
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
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
            LogicButton(
                text = stringResource(R.string.mydroid_advanced),
                backgroundColor = MyDroidComposeColors.LogicAdvanced,
                contentPadding = PaddingValues(top = 16.dp),
                onClick = { showAdvanced = true },
            )
            Spacer(modifier = Modifier.height(21.dp))
        }
    }
    if (showAdvanced) {
        TransferTheme {
            AlertDialog(
                onDismissRequest = { showAdvanced = false },
                title = { Text(stringResource(R.string.mydroid_advanced)) },
                text = {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.mydroid_install_apk)) },
                        modifier = Modifier.clickable(role = Role.Button) {
                            showAdvanced = false
                            onInstallApk()
                        },
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showAdvanced = false }) {
                        Text(stringResource(R.string.connect_to_host_cancel_upload))
                    }
                },
            )
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
                .heightIn(min = 62.dp)
                .background(backgroundColor, RoundedCornerShape(16.dp))
                .noBackClickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 15.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = ComposeTypography.Font20MWhite,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ------------------------- client 雷达扫描 UI -------------------------

/** 客户端雷达将搜索入口放在中心，周围八个槽位展示发现的主机。 */
@Composable
private fun ClientPage(
    discoveredHosts: List<DiscoveredHost>,
    scanning: Boolean,
    localIp: String?,
    onStartSearch: () -> Unit,
    onIpClick: (DiscoveredHost) -> Unit,
) {
    val slotMap = remember(discoveredHosts) { assignSlots(discoveredHosts) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // 雷达底盘 + 扫描动画 + 8 槽位 3x3 网格
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            RadarBasePlate(scanning = scanning)

            Column(Modifier.fillMaxSize()) {
                Row(Modifier.weight(1f)) {
                    SlotCell(slotMap[0], localIp, onIpClick, Modifier.weight(1f))
                    SlotCell(slotMap[1], localIp, onIpClick, Modifier.weight(1f))
                    SlotCell(slotMap[2], localIp, onIpClick, Modifier.weight(1f))
                }
                Row(Modifier.weight(1f)) {
                    SlotCell(slotMap[3], localIp, onIpClick, Modifier.weight(1f))
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        if (!scanning) {
                            val label = if (discoveredHosts.isEmpty()) R.string.btn_start_search else R.string.cd_rescan
                            StartSearchButton(text = stringResource(label), onClick = onStartSearch)
                        }
                    }
                    SlotCell(slotMap[4], localIp, onIpClick, Modifier.weight(1f))
                }
                Row(Modifier.weight(1f)) {
                    SlotCell(slotMap[5], localIp, onIpClick, Modifier.weight(1f))
                    SlotCell(slotMap[6], localIp, onIpClick, Modifier.weight(1f))
                    SlotCell(slotMap[7], localIp, onIpClick, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StartSearchButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .wrapContentWidth()
            .wrapContentHeight()
            .background(ComposeColors.BlueLink, RoundedCornerShape(6.dp))
            .noBackClickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = ComposeTypography.Font16MWhite.copy(textAlign = TextAlign.Center),
        )
    }
}

/** 3x3 网格中的一个槽位; host 为 null 时留空。 */
@Composable
private fun RowScope.SlotCell(
    host: DiscoveredHost?,
    localIp: String?,
    onIpClick: (DiscoveredHost) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (host != null) {
            val (text, textColor) = ipDisplayTextAndColor(localIp, host.ip)
            Box(
                modifier = Modifier
                    .background(MyDroidComposeColors.NormalBlock, RoundedCornerShape(4.dp))
                    .noBackClickable { onIpClick(host) }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                Text(
                    text = text,
                    style = ComposeTypography.Font16M,
                    color = textColor,
                )
            }
        }
    }
}

/**
 * 按 (host.ip.hashCode() and 0x7fffffff) % 8 分配 8 槽位,冲突时顺延; 8 槽位已满则丢弃新 host。
 */
private fun assignSlots(hosts: List<DiscoveredHost>): Map<Int, DiscoveredHost> {
    val slots = arrayOfNulls<DiscoveredHost?>(8)
    val used = mutableSetOf<String>()
    for (host in hosts) {
        if (host.ip in used) continue
        var slot = (host.ip.hashCode() and 0x7fffffff) % 8
        var tries = 0
        while (slots[slot] != null && tries < 8) {
            slot = (slot + 1) % 8
            tries++
        }
        if (slots[slot] != null) {
            logDebug("radar slots full, drop host ${host.ip}")
            continue
        }
        slots[slot] = host
        used.add(host.ip)
    }
    return slots.mapIndexedNotNull { i, h -> h?.let { i to it } }.toMap()
}

/**
 * IP 文案显示规则: 本地 IP 与 host IP 都 split('.') 拆 4 段,找第一个不一致的段索引 i。
 * - i == 3 (前 3 段全一致, 同网段): 显示末两段 (如 110.12), 正文色。
 * - i < 3 (从第 i 段起不一致, 跨网段): 从第 i 段开始显示到末尾, 灰色。
 * - 本地 IP 为 null 或段数不足 4: 一律按不一致处理, 显示 host 完整 IP, 描述色。
 */
@Composable
private fun ipDisplayTextAndColor(localIp: String?, hostIp: String): Pair<String, Color> {
    val local = localIp?.split('.')
    val remote = hostIp.split('.')
    if (local == null || local.size < 4 || remote.size < 4) {
        return hostIp to ComposeColors.TextDesc
    }
    var firstDiff = 4
    for (i in 0 until 4) {
        if (local[i] != remote[i]) {
            firstDiff = i
            break
        }
    }
    return if (firstDiff == 4 || firstDiff == 3) {
        // 前 3 段全一致: 显示末两段
        remote.takeLast(2).joinToString(".") to ComposeColors.TextPrimary
    } else {
        // 从 firstDiff 段起不一致: 从该段显示到末尾
        remote.drop(firstDiff).joinToString(".") to ComposeColors.TextDesc
    }
}


@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun MyDroidAllPreview() {
    com.au.module_androiduiex.preview.AppPreview {
        MyDroidAllScreen(MyDroidAllUiState(null, true), {}, {}, {}, {}, emptyList(), false, null, {}, {})
    }
}
