(function() {
    let connectTimer = null;
    let heartbeatTimer = null;
    let handshakeTimer = null;
    let pendingRequest = null;

    window.WS = null;
    window.READ_WEBSOCKET_IP_PORT = "/read-websocket-ip-port";
    window.API_WS_LEFT_SPACE = "s_leftSpace";
    window.API_WS_CLIENT_INIT_CALLBACK = "s_clientInitBack";
    window.API_WS_INIT = "c_wsInit";
    window.API_WS_PING = "c_ping";
    window.HEARTBEAT_INTERVAL = 12000;
    window.mydroidIpPort = {};

    function stopHeartbeatInterval() {
        clearInterval(heartbeatTimer);
        heartbeatTimer = null;
        clearTimeout(handshakeTimer);
        handshakeTimer = null;
    }

    function setupWSFileList(port) {
        // 使用浏览器实际访问的入口，兼容热点、多网卡和 IPv6。
        const url = new URL(window.location.href);
        url.protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
        url.port = String(port);
        url.pathname = "/ws-test";
        url.search = "";
        url.hash = "";
        const socket = new WebSocket(url.href);
        window.WS = socket;
        handshakeTimer = setTimeout(() => {
            if (window.WS === socket) socket.close();
        }, 10000);
        socket.onopen = () => {
            if (window.WS !== socket) return;
            socket.send(JSON.stringify({
                api: window.API_WS_INIT,
                wsName: generateUUID().substring(0, 6),
                platform: "html"
            }));
        };
        socket.onmessage = event => {
            if (window.WS !== socket) return;
            try {
                const frame = JSON.parse(event.data);
                if (frame.api === window.API_WS_CLIENT_INIT_CALLBACK) {
                    stopHeartbeatInterval();
                    heartbeatTimer = setInterval(() => {
                        if (socket.readyState === WebSocket.OPEN) {
                            socket.send(JSON.stringify({ api: window.API_WS_PING }));
                        }
                    }, window.HEARTBEAT_INTERVAL);
                }
                parseMessage(event.data);
            } catch (error) {
                console.error("WebSocket message parse failed", error);
            }
        };
        socket.onerror = error => console.error("WebSocket failed", error);
        socket.onclose = () => {
            if (window.WS !== socket) return;
            window.WS = null;
            stopHeartbeatInterval();
            commonHtmlConnectError();
        };
    }

    async function startWebSocket() {
        connectTimer = null;
        if (pendingRequest || window.WS) return;
        const controller = new AbortController();
        pendingRequest = controller;
        const timeout = setTimeout(() => controller.abort(), 10000);
        try {
            const response = await fetch(window.READ_WEBSOCKET_IP_PORT, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: "{}",
                signal: controller.signal
            });
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            const result = await response.json();
            if (controller.signal.aborted || pendingRequest !== controller) return;
            const port = result?.data?.port;
            if (String(result.code) !== "0" || !Number.isInteger(port) || port < 1 || port > 65535) {
                throw new Error("Invalid WebSocket endpoint");
            }
            window.mydroidIpPort = {
                ip: window.location.hostname,
                port,
                httpPort: window.location.port || (window.location.protocol === "https:" ? 443 : 80)
            };
            setupWSFileList(port);
        } catch (error) {
            if (pendingRequest !== controller) return;
            console.error("Read WebSocket endpoint failed", error);
            commonHtmlConnectError();
        } finally {
            clearTimeout(timeout);
            if (pendingRequest === controller) pendingRequest = null;
        }
    }

    window.startWsConnect = function startWsConnect() {
        if (connectTimer || pendingRequest || window.WS) return;
        connectTimer = setTimeout(startWebSocket, 0);
    };

    window.addEventListener("pagehide", () => {
        clearTimeout(connectTimer);
        connectTimer = null;
        const request = pendingRequest;
        pendingRequest = null;
        request?.abort();
        const socket = window.WS;
        window.WS = null;
        stopHeartbeatInterval();
        socket?.close();
    });
    window.addEventListener("pageshow", event => {
        if (event.persisted) window.startWsConnect();
    });
})();
