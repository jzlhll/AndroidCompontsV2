(function() {
    const REQUEST_FILE_LIST = "/request-file-list";

    async function requestFileList() {
            let response = null;
            try {
                response = await fetch(REQUEST_FILE_LIST, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                });
            } catch(e) {}
            if (!response) throw new Error(loc["failure_error_with_code"] + "101");
            if (!response.ok) throw new Error(loc["failure_error_with_code"] + "102 " + (await response.text()));
            const response = await response.json();
            console.log("request file list response:", response);
            htmlShowFileList(response.urlRealInfoHtmlList);
        }

    window.parseMessage = async function(eventData) {
        const jsonData = JSON.parse(eventData);
        const data = jsonData.data;
        const api = jsonData.api;

        if (api == API_WS_CLIENT_INIT_CALLBACK) {
            htmlUpdateIpClient(data.myDroidMode, data.clientName, data.color);
            requestFileList();
            return true;
        }

        return false;
    }
})();