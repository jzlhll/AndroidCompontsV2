package com.allan.mydroid.views.compose

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.allan.mydroid.R
import com.allan.mydroid.bt.DiscoveredHost
import com.au.module_android.log.logDebug
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
 * - client 页：BLE 雷达扫描 + 8 槽位 IP 矩形。
 */
@Composable
fun MyDroidAllScreen(
    uiState: MyDroidAllUiState,
    onReceiveFile: () -> Unit,
    onSendFile: () -> Unit,
    onTextChat: () -> Unit,
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
                .noBackClickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = text,
                style = ComposeTypography.Font20MWhite.copy(textAlign = TextAlign.Center),
            )
        }
    }
}

// ------------------------- client 雷达扫描 UI -------------------------

/** 雷达底盘背景色（浅黄）。 */
private val RadarBaseColor = Color(0xFFFFF8E1)
/** 雷达底盘描边色（偏橙）。 */
private val RadarBaseStrokeColor = Color(0xFFFFB74D)
/** 雷达水波纹扩散圆颜色（偏橙）。 */
private val RadarWaveColor = Color(0xFFFF9800)
/** rescan 图标 tint。 */
private val RescanIconColor = Color(0xFF666666)
/** IP 矩形背景色（与页面背景一致）。 */
private val IpBoxBg = Color.White
/** 同网段 IP 文字色。 */
private val IpTextSameSubnet = Color.Black
/** 跨网段 IP 文字色。 */
private val IpTextDiffSubnet = Color(0xFF999999)

/**
 * client Tab 雷达 UI。
 * - scanning=true: 雷达水波纹动画启动,隐藏 rescan 与开始搜索按钮。
 * - scanning=false: 雷达底盘静态,右上角 rescan 图标; 无 host 时雷达中心显示「开始搜索」按钮; 有 host 时按钮隐藏、IP 矩形显示。
 */
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

            // 3x3 网格叠加在雷达上,中心格留给"开始搜索"按钮
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.weight(1f)) {
                    SlotCell(slotMap[0], localIp, onIpClick, Modifier.weight(1f))
                    SlotCell(slotMap[1], localIp, onIpClick, Modifier.weight(1f))
                    SlotCell(slotMap[2], localIp, onIpClick, Modifier.weight(1f))
                }
                Row(Modifier.weight(1f)) {
                    SlotCell(slotMap[3], localIp, onIpClick, Modifier.weight(1f))
                    Spacer(Modifier.weight(1f))
                    SlotCell(slotMap[4], localIp, onIpClick, Modifier.weight(1f))
                }
                Row(Modifier.weight(1f)) {
                    SlotCell(slotMap[5], localIp, onIpClick, Modifier.weight(1f))
                    SlotCell(slotMap[6], localIp, onIpClick, Modifier.weight(1f))
                    SlotCell(slotMap[7], localIp, onIpClick, Modifier.weight(1f))
                }
            }

            // 中心"开始搜索"按钮: 仅未扫描且无 host 时显示
            if (!scanning && discoveredHosts.isEmpty()) {
                StartSearchButton(onStartSearch)
            }
        }

        // 右上角 rescan 图标: 仅未扫描时显示
        if (!scanning) {
            Icon(
                painter = painterResource(R.drawable.ic_rescan),
                contentDescription = stringResource(R.string.cd_rescan),
                tint = RescanIconColor,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp, top = 8.dp)
                    .size(24.dp)
                    .noBackClickable(onClick = onStartSearch),
            )
        }
    }
}

/** 雷达底盘: 固定圆 + 扫描时叠加 3 个错峰扩散圆。 */
@Composable
private fun BoxScope.RadarBasePlate(scanning: Boolean) {
    val transition = rememberInfiniteTransition(label = "radarWave")
    val anim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
        ),
        label = "radarWaveAnim",
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val center = Offset(canvasWidth / 2f, canvasHeight / 2f)
        val maxRadius = minOf(canvasWidth, canvasHeight) / 2f
        val strokePx = 2.dp.toPx()

        // 雷达底盘径向渐变：中心深、边缘淡
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(RadarBaseColor, RadarBaseColor.copy(alpha = 0.3f)),
                center = center,
                radius = maxRadius,
                tileMode = TileMode.Clamp,
            ),
            radius = maxRadius,
            center = center,
        )

        // 扫描时叠加 3 个错峰扩散圆
        if (scanning) {
            val offsets = floatArrayOf(0f, 0.33f, 0.66f)
            offsets.forEach { offset ->
                val progress = (anim.value + offset) % 1f
                val radius = maxRadius * progress
                val alpha = (1f - progress) * 0.8f
                drawCircle(
                    color = RadarWaveColor.copy(alpha = alpha),
                    radius = radius,
                    center = center,
                    style = Stroke(width = strokePx),
                )
            }
        }
    }
}

/** 雷达中心「开始搜索」按钮。 */
@Composable
private fun BoxScope.StartSearchButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .wrapContentWidth()
            .wrapContentHeight()
            .background(RadarBaseStrokeColor, RoundedCornerShape(6.dp))
            .noBackClickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = stringResource(R.string.btn_start_search),
            style = ComposeTypography.Font16M.copy(color = Color.White, textAlign = TextAlign.Center),
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
                    .background(IpBoxBg, RoundedCornerShape(4.dp))
                    .noBackClickable { onIpClick(host) }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                BasicText(
                    text = text,
                    style = ComposeTypography.Font16M.copy(color = textColor),
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
 * - i == 3 (前 3 段全一致, 同网段): 显示末两段 (如 110.12), 黑色。
 * - i < 3 (从第 i 段起不一致, 跨网段): 从第 i 段开始显示到末尾, 灰色。
 * - 本地 IP 为 null 或段数不足 4: 一律按不一致处理, 显示 host 完整 IP, 灰色。
 */
private fun ipDisplayTextAndColor(localIp: String?, hostIp: String): Pair<String, Color> {
    val local = localIp?.split('.')
    val remote = hostIp.split('.')
    if (local == null || local.size < 4 || remote.size < 4) {
        return hostIp to IpTextDiffSubnet
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
        remote.takeLast(2).joinToString(".") to IpTextSameSubnet
    } else {
        // 从 firstDiff 段起不一致: 从该段显示到末尾
        remote.drop(firstDiff).joinToString(".") to IpTextDiffSubnet
    }
}
