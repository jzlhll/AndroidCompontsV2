(function() {
    window.API_WS_TEXT_CHAT_SEND = "c_textChat";
    window.API_WS_TEXT_CHAT_CALLBACK = "s_textChat";

    const colorPalette = [
        "#3D7C42",
        "#FF9E80",
        "#6A3188",
        "#895DF8",
        "#CEBE55",
        "#5CCE99",
        "#CE626E",
        "#71A3CE",
    ];
    const colorCache = new Map();
    const messageKeys = new Set();
    const messageTemplate = document.getElementById("chat-message");
    const idleTimeout = 3 * 60 * 1000;

    let selfIp = "browser";
    let selfColor = "#7B4DFF";

    const selfAddressView = document.getElementById("chatSelfAddress");
    const hostAddressView = document.getElementById("chatHostAddress");
    const titleColorIcon = document.getElementById("titleColorIcon");
    const messageListView = document.getElementById("messageList");
    const messageInput = document.getElementById("messageInput");
    const sendButton = document.getElementById("sendButton");
    const copyToast = document.getElementById("copyToast");
    const contextMenu = document.getElementById("messageContextMenu");
    const copyMenuItem = document.getElementById("copyMenuItem");
    const idleDialog = document.getElementById("idleDialog");
    const idlePrompt = document.getElementById("idlePrompt");
    const idleContinueButton = document.getElementById("idleContinueButton");
    const idleExitButton = document.getElementById("idleExitButton");
    const overlayView = document.getElementById("overlayView");
    const overlayError1 = document.getElementById("overlayError1");
    const overlayError2 = document.getElementById("overlayError2");
    let chatConnected = false;
    let idleTimer = null;

    document.documentElement.lang = loc["text_chat_lang"];
    document.getElementById("chatTitle").textContent = loc["text_chat_browser_title"];
    hostAddressView.textContent = loc["text_chat_connecting"];
    messageInput.placeholder = loc["text_chat_browser_placeholder"];
    sendButton.textContent = loc["text_chat_browser_send"];
    copyMenuItem.textContent = loc["copy"];
    idlePrompt.textContent = loc["text_chat_idle_prompt"];
    idleContinueButton.textContent = loc["text_chat_idle_continue"];
    idleExitButton.textContent = loc["text_chat_idle_exit"];
    overlayError1.textContent = loc["connection_lost"];
    overlayError2.textContent = loc["reconnect_prompt"];

    function restartIdleTimer() {
        clearTimeout(idleTimer);
        if (idleDialog.open) idleDialog.close();
        if (!chatConnected || document.visibilityState !== "visible") return;

        idleTimer = setTimeout(() => {
            if (document.visibilityState === "visible" && window.WS?.readyState === WebSocket.OPEN) {
                hideContextMenu();
                idleDialog.showModal();
            }
        }, idleTimeout);
    }

    function stopIdleTimer() {
        chatConnected = false;
        selfAddressView.textContent = "";
        hostAddressView.textContent = "";
        restartIdleTimer();
    }

    function onChatInteraction(event) {
        if (event.isTrusted && !idleDialog.open) restartIdleTimer();
    }

    function hashIp(ip) {
        let hash = 0;
        for (let i = 0; i < ip.length; i++) {
            hash = ((hash << 5) - hash) + ip.charCodeAt(i);
            hash |= 0;
        }
        return Math.abs(hash);
    }

    function getIconColor(ip) {
        if (!colorCache.has(ip)) {
            const color = colorPalette[hashIp(ip) % colorPalette.length];
            colorCache.set(ip, color);
        }
        return colorCache.get(ip);
    }

    function appendMessage(type, text, ip, timestamp = Date.now(), iconColor = "") {
        const message = (text || "").trim();
        if (!message) return;
        const host = ip || "0.0.0.0";
        const key = JSON.stringify([host, timestamp, message]);
        if (messageKeys.has(key)) return;
        const row = messageTemplate.content.firstElementChild.cloneNode(true);
        row.classList.add(type);
        const icon = row.querySelector(".message-icon");
        if (type === "client") {
            icon.className = "message-icon-star";
            icon.textContent = "🌟";
        } else {
            icon.style.backgroundColor = iconColor || getIconColor(host);
        }
        row.querySelector(".message-host").textContent = host;
        row.querySelector(".message-time").textContent = formatTimestamp(timestamp);
        row.querySelector(".message-text").textContent = message;
        messageListView.appendChild(row);
        messageKeys.add(key);
        scrollMessagesToBottom();
        restartIdleTimer();
    }

    function scrollMessagesToBottom() {
        requestAnimationFrame(() => {
            messageListView.scrollTop = messageListView.scrollHeight;
        });
    }

    function sendCurrentMessage() {
        const text = messageInput.value.trim();
        if (!text) {
            return;
        }
        if (!window.WS || window.WS.readyState !== WebSocket.OPEN) {
            showCopyToast(loc["connection_lost"]);
            return;
        }
        const timestamp = Date.now();
        window.WS.send(JSON.stringify({
            api: window.API_WS_TEXT_CHAT_SEND,
            textBase64: encodeBase64(text),
            timestamp,
            iconColor: selfColor,
        }));
        appendMessage("self", text, selfIp, timestamp, selfColor);
        messageInput.value = "";
        resizeInput();
        messageInput.focus();
    }

    function resizeInput() {
        messageInput.style.height = "40px";
        const nextHeight = Math.min(messageInput.scrollHeight, 120);
        messageInput.style.height = `${nextHeight}px`;
        messageInput.style.overflowY = messageInput.scrollHeight > 120 ? "auto" : "hidden";
    }

    async function copyText(text) {
        if (!text) {
            return;
        }
        try {
            if (navigator.clipboard && navigator.clipboard.writeText) {
                await navigator.clipboard.writeText(text);
            } else {
                fallbackCopyText(text);
            }
            showCopyToast(loc["text_chat_copy_success"]);
        } catch (_) {
            fallbackCopyText(text);
            showCopyToast(loc["text_chat_copy_success"]);
        }
    }

    function fallbackCopyText(text) {
        const textArea = document.createElement("textarea");
        textArea.value = text;
        textArea.style.position = "fixed";
        textArea.style.opacity = "0";
        document.body.appendChild(textArea);
        textArea.focus();
        textArea.select();
        document.execCommand("copy");
        document.body.removeChild(textArea);
    }

    let toastTimer = null;
    function showCopyToast(text) {
        copyToast.textContent = text;
        copyToast.classList.add("show");
        clearTimeout(toastTimer);
        toastTimer = setTimeout(() => {
            copyToast.classList.remove("show");
        }, 1200);
    }

    let currentContextText = "";
    function hideContextMenu() {
        contextMenu.style.display = "none";
        currentContextText = "";
    }

    function showContextMenu(x, y, text) {
        currentContextText = text;
        contextMenu.style.display = "block";
        const menuWidth = contextMenu.offsetWidth || 110;
        const menuHeight = contextMenu.offsetHeight || 48;
        const left = Math.min(x, window.innerWidth - menuWidth - 8);
        const top = Math.min(y, window.innerHeight - menuHeight - 8);
        contextMenu.style.left = `${Math.max(8, left)}px`;
        contextMenu.style.top = `${Math.max(8, top)}px`;
    }

    messageListView.addEventListener("contextmenu", (event) => {
        const target = event.target.closest(".message-text");
        if (target) {
            event.preventDefault();
            showContextMenu(event.clientX, event.clientY, target.textContent);
        }
    });

    copyMenuItem.addEventListener("click", async () => {
        if (!currentContextText) {
            hideContextMenu();
            return;
        }
        await copyText(currentContextText);
        hideContextMenu();
    });

    document.addEventListener("click", (event) => {
        if (!contextMenu.contains(event.target)) {
            hideContextMenu();
        }
    });

    window.addEventListener("scroll", hideContextMenu, true);
    window.addEventListener("resize", hideContextMenu);

    sendButton.addEventListener("click", sendCurrentMessage);

    for (const eventName of ["pointerdown", "click", "keydown", "wheel"]) {
        document.addEventListener(eventName, onChatInteraction, {capture: true, passive: true});
    }
    messageInput.addEventListener("input", (event) => {
        resizeInput();
        onChatInteraction(event);
    });
    messageListView.addEventListener("scroll", onChatInteraction);

    idleContinueButton.addEventListener("click", restartIdleTimer);
    idleDialog.addEventListener("cancel", (event) => {
        event.preventDefault();
        restartIdleTimer();
    });
    idleExitButton.addEventListener("click", () => {
        stopIdleTimer();
        window.location.replace("about:blank");
    });
    document.addEventListener("visibilitychange", restartIdleTimer);
    window.addEventListener("pagehide", stopIdleTimer);
    window.addEventListener("pageshow", (event) => {
        if (event.persisted) hostAddressView.textContent = loc["text_chat_connecting"];
    });

    window.commonHtmlConnectError = function() {
        stopIdleTimer();
        hideContextMenu();
        overlayView.style.display = "flex";
        document.body.style.overflow = "hidden";
    };

    window.parseMessage = function(eventData) {
        const jsonData = JSON.parse(eventData);
        const data = jsonData.data;
        const api = jsonData.api;

        if (api === window.API_WS_CLIENT_INIT_CALLBACK) {
            const clientName = data.clientName || "";
            selfIp = clientName || "browser";
            selfColor = getIconColor(selfIp);
            if (titleColorIcon) {
                titleColorIcon.style.background = selfColor;
            }
            const {ip, httpPort} = window.mydroidIpPort;
            selfAddressView.textContent = clientName;
            hostAddressView.textContent = loc["text_chat_host_address"].replace("{address}", `${ip}:${httpPort}`);
            if (!chatConnected) {
                overlayView.style.display = "";
                chatConnected = true;
                restartIdleTimer();
            }
            return true;
        }

        if (api === window.API_WS_TEXT_CHAT_CALLBACK) {
            const text = decodeBase64(data.textBase64);
            if (text) {
                appendPhoneMessage(text, data.ip, data.timestamp, data.iconColor);
            }
            return true;
        }

        return false;
    };

    window.appendBrowserMessage = function(text, ip = "browser", timestamp = Date.now(), iconColor = selfColor) {
        appendMessage("self", text, ip, timestamp, iconColor);
    };

    window.appendPhoneMessage = function(text, ip = "phone", timestamp = Date.now(), iconColor = "") {
        appendMessage(ip === selfIp ? "self" : "client", text, ip, timestamp, iconColor);
    };

    function encodeBase64(text) {
        return btoa(unescape(encodeURIComponent(text)));
    }

    function decodeBase64(textBase64) {
        try {
            return decodeURIComponent(escape(atob(textBase64 || "")));
        } catch (_) {
            return "";
        }
    }

    function formatTimestamp(timestamp) {
        const date = new Date(timestamp ?? Date.now());
        const month = `${date.getMonth() + 1}`.padStart(2, "0");
        const day = `${date.getDate()}`.padStart(2, "0");
        const hour = `${date.getHours()}`.padStart(2, "0");
        const minute = `${date.getMinutes()}`.padStart(2, "0");
        return `${month}-${day} ${hour}:${minute}`;
    }

    resizeInput();
    startWsConnect();
})();
