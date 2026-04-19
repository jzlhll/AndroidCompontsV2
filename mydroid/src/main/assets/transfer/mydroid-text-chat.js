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
    const messageList = [];

    let selfIp = "browser";
    let selfHost = "web";
    let selfColor = "#7B4DFF";

    const subtitleView = document.getElementById("chatSubtitle");
    const messageListView = document.getElementById("messageList");
    const messageInput = document.getElementById("messageInput");
    const sendButton = document.getElementById("sendButton");
    const copyToast = document.getElementById("copyToast");
    const contextMenu = document.getElementById("messageContextMenu");
    const copyMenuItem = document.getElementById("copyMenuItem");
    const overlayError1 = document.getElementById("overlayError1");
    const overlayError2 = document.getElementById("overlayError2");

    subtitleView.textContent = loc["text_chat_browser_subtitle"];
    messageInput.placeholder = loc["text_chat_browser_placeholder"];
    sendButton.textContent = loc["text_chat_browser_send"];
    copyMenuItem.textContent = loc["copy"];
    overlayError1.textContent = loc["connection_lost"];
    overlayError2.textContent = loc["reconnect_prompt"];

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

    function renderMessages() {
        const html = messageList.map((item) => {
            const host = item.host && item.host.length > 0 ? item.host : "-";
            const timeText = formatTimestamp(item.timestamp);
            const escapedText = escapeHtml(item.text);
            const iconColor = item.iconColor || getIconColor(item.ip);
            return `
                <div class="message-item ${item.type}">
                    <div class="message-content">
                        <div class="message-top">
                            <div class="message-icon" style="background:${iconColor};"></div>
                            <div class="message-host">${escapeHtml(item.ip)}:${escapeHtml(host)}&nbsp;&nbsp;${timeText}</div>
                        </div>
                        <div class="message-bubble">
                            <div class="message-text" data-copy-text="${escapeAttr(item.text)}">${escapedText}</div>
                        </div>
                    </div>
                </div>
            `;
        }).join("");
        messageListView.innerHTML = html;
        scrollMessagesToBottom();
    }

    function appendMessage(type, text, ip, host, timestamp = Date.now(), iconColor = "") {
        const trimText = (text || "").trim();
        if (!trimText) {
            return;
        }
        messageList.push({
            type,
            text: trimText,
            ip: ip || "0.0.0.0",
            host: host || "-",
            timestamp,
            iconColor,
        });
        renderMessages();
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
        appendMessage("self", text, selfIp, selfHost, timestamp, selfColor);
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
            showContextMenu(event.clientX, event.clientY, target.dataset.copyText || "");
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

    messageInput.addEventListener("input", resizeInput);

    window.commonHtmlConnectError = function() {
        const overlay = document.getElementById("overlayView");
        overlay.style.display = "flex";
        document.body.style.overflow = "hidden";
    };

    window.parseMessage = function(eventData) {
        const jsonData = JSON.parse(eventData);
        const data = jsonData.data;
        const api = jsonData.api;

        if (api === window.API_WS_CLIENT_INIT_CALLBACK) {
            const clientName = data.clientName || "";
            selfIp = clientName.includes("@") ? clientName.split("@")[0] : "browser";
            selfHost = clientName.includes("@") ? clientName.split("@")[1] : "web";
            selfColor = data.color || selfColor;
            subtitleView.textContent = `${loc["text_chat_browser_subtitle"]} · ${data.clientName}`;
            return true;
        }

        if (api === window.API_WS_TEXT_CHAT_CALLBACK) {
            const text = decodeBase64(data.textBase64);
            if (text) {
                appendPhoneMessage(text, data.ip, data.host, data.timestamp, data.iconColor);
            }
            return true;
        }

        return false;
    };

    window.appendBrowserMessage = function(text, ip = "browser", host = "web", timestamp = Date.now(), iconColor = selfColor) {
        appendMessage("self", text, ip, host, timestamp, iconColor);
    };

    window.appendPhoneMessage = function(text, ip = "phone", host = "app", timestamp = Date.now(), iconColor = "") {
        appendMessage("client", text, ip, host, timestamp, iconColor);
    };

    function escapeHtml(text) {
        return String(text)
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll("\"", "&quot;")
            .replaceAll("'", "&#39;")
            .replaceAll("\n", "<br>");
    }

    function escapeAttr(text) {
        return String(text)
            .replaceAll("&", "&amp;")
            .replaceAll("\"", "&quot;")
            .replaceAll("'", "&#39;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll("\n", "&#10;");
    }

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
        const date = new Date(timestamp || Date.now());
        const hour = `${date.getHours()}`.padStart(2, "0");
        const minute = `${date.getMinutes()}`.padStart(2, "0");
        return `${hour}:${minute}`;
    }

    resizeInput();
    startWsConnect();
})();
