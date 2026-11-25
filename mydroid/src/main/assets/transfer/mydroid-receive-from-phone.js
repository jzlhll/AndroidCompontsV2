(function() {
    const REQUEST_FILE_LIST = "/request-file-list";

    let pendingRequest = null;
    window.requestFileList = async function() {
        if (pendingRequest) pendingRequest.abort();
        const controller = new AbortController();
        pendingRequest = controller;
        const status = document.getElementById('list-status');
        const retry = document.getElementById('retry-list');
        document.getElementById('loading-text').style.display = 'block';
        retry.hidden = true;
        status.textContent = loc['file_list_loading'];
        const timeout = setTimeout(() => controller.abort(), 15000);
        try {
            const response = await fetch(REQUEST_FILE_LIST, { method: 'POST', signal: controller.signal });
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            const result = await response.json();
            if (String(result.code) !== "0") throw new Error(result.msg || 'File list request failed');
            const files = JSON.parse(result.data);
            if (!Array.isArray(files)) throw new Error('Invalid file list');
            if (pendingRequest === controller) htmlShowFileList(files);
        } catch (error) {
            if (pendingRequest !== controller) return;
            console.error('Load file list failed', error);
            status.textContent = loc['file_list_failed'];
            retry.hidden = false;
        } finally {
            clearTimeout(timeout);
            if (pendingRequest === controller) pendingRequest = null;
        }
    };

    window.addEventListener('pagehide', () => {
        if (pendingRequest) pendingRequest.abort();
        pendingRequest = null;
    });

    window.parseMessage = function(eventData) {
        const jsonData = JSON.parse(eventData);
        const data = jsonData.data;
        const api = jsonData.api;

        if (api == API_WS_CLIENT_INIT_CALLBACK) {
            htmlUpdateIpClient(data.myDroidMode, data.clientName, data.color);
            window.requestFileList();
            return true;
        }

        return false;
    }
  })();