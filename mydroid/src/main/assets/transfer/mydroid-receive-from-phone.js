(function() {
    window.parseMessage = async function(eventData) {
        const jsonData = JSON.parse(eventData);
        const data = jsonData.data;
        const api = jsonData.api;

        if (api == API_WS_CLIENT_INIT_CALLBACK) {
            htmlUpdateIpClient(data.myDroidMode, data.clientName, data.color);
            return true;
        } else if (api == API_WS_SEND_FILE_LIST) {
            htmlShowFileList(data.urlRealInfoHtmlList);
            return true;
        }

        return false;
    }
})();