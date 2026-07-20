package com.allan.mydroid.client

import com.allan.mydroid.api.MyDroidMode

/**
 * client 端连到 host 的目标信息。BLE 扫描时刻的 mode 仅作为初始路由依据，
 * 后续 host 切 mode 会主动 close WS（见 WebsocketServer.startObserveModeChange），
 * client 通过 connectionStateFlow 感知后提示用户返回。
 *
 * @param httpPort host 的 HTTP 端口（BLE 广播的 port 即此）
 * @param wsPort host 的 WebSocket 端口（由 /read-websocket-ip-port 响应给出）
 */
data class HostEndpoint(
    val ip: String,
    val httpPort: Int,
    val wsPort: Int,
    val mode: MyDroidMode
)
