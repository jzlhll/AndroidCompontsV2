(function() {
    const MERGE_CHUNKS = "/merge-chunks";
    const UPLOAD_CHUNK = "/upload-chunk";
    const ABORT_UPLOAD_CHUNKS = "/abort-upload-chunks";


    window.parseMessage = function(eventData) {
        const jsonData = JSON.parse(eventData);
        const data = jsonData.data;
        const api = jsonData.api;
        //const msg = jsonData.msg;
        if (api == API_WS_LEFT_SPACE) {
            htmlUpdateLeftSpace(loc["remaining_phone_space"] + data.leftSpace);
            return true;
        } else if (api == API_WS_CLIENT_INIT_CALLBACK) {
            return true;
        }
        return false;
    }

    // 上传分片
    async function uploadChunk(formData, signal) {
        let response = null;
        try {
            response = await fetch(UPLOAD_CHUNK, {
                    method: 'POST',
                    body: formData,
                    signal,
                });
        } catch(e) {}
        if (!response) throw new Error(loc["error_upload_failure_e01"]);
        if (!response.ok) throw new Error(loc["error_upload_failure_e02"] + (await response.text()));
        return await response.json();
    }

    // 通知服务器合并分片
    async function mergeChunks(md5, fileName, totalChunks, lastModified, uploadId) {
        let response = null;
        try {
            response = await fetch(MERGE_CHUNKS, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ md5, fileName, totalChunks, lastModified, uploadId, hashMode: 'stream' }),
            });
        } catch(e) {}
        if (!response) throw new Error(loc["error_upload_failure_e03"]);
        if (!response.ok) throw new Error(loc["error_upload_failure_e04"] + (await response.text()));
        return await response.json();
    }

    async function abortUploadChunk(fileName, uploadId) {
        let response = null;
        try {
            response = await fetch(ABORT_UPLOAD_CHUNKS, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ fileName, uploadId, hashMode: 'stream' }),
                signal: AbortSignal.timeout(5000),
                keepalive: true,
            });
        } catch(e) {}
        if (!response) throw new Error(loc["error_upload_failure_e05"]);
        if (!response.ok) throw new Error(loc["error_upload_failure_e06"] + (await response.text()));
        return await response.json();
    }

    // 根据文件大小限制分块内存，并减少大文件请求次数。
    function getChunkSize(fileSize) {
        const MB = 1024 * 1024;
        if (fileSize <= 10 * MB) {
            return MB / 2;
        } else if (fileSize <= 100 * MB) {
            return 3 * MB;
        } else if (fileSize <= 500 * MB) {
            return 4 * MB;
        } else {
            return 5 * MB;
        }
    }

    let activeUpload = null;
    window.cancelActiveUpload = function() { activeUpload?.cancel(); };
    window.addEventListener('pagehide', () => activeUpload?.cancel());

    window.startUploadFile = async function(startTs, file, onProgress = () => {}) {
        if (!file) throw new Error(loc["no_file_selected"]);
        const uploadId = generateUUID();
        const fileName = file.name;
        const chunkSize = getChunkSize(file.size);
        const chunks = Math.max(1, Math.ceil(file.size / chunkSize));
        const controller = new AbortController();
        let hasher = null;
        let merging = false;
        let completed = false;
        const job = { cancel: () => {
            // 合并开始后等待确定结果，避免已落盘文件被误报为取消。
            if (merging) return;
            controller.abort();
            hasher?.cancel();
        } };
        activeUpload = job;
        const checkActive = () => {
            if (controller.signal.aborted || startTs !== window.lastStartTsFlagArr.startUploadTime) {
                throw new DOMException('Upload canceled', 'AbortError');
            }
        };
        try {
            hasher = await createUploadHasher(controller.signal);
            let sentBytes = 0;
            const startedAt = performance.now();
            for (let i = 0; i < chunks; i++) {
                checkActive();
                const buffer = await file.slice(i * chunkSize, (i + 1) * chunkSize).arrayBuffer();
                checkActive();
                const length = buffer.byteLength;
                const formData = new FormData();
                formData.append('fileName', fileName);
                // Blob 先保留上传字节，再将 ArrayBuffer 所有权交给 Worker。
                formData.append('chunk', new Blob([buffer]), fileName);
                formData.append('chunkIndex', i + 1);
                formData.append('totalChunks', chunks);
                formData.append('uploadId', uploadId);
                formData.append('hashMode', 'stream');
                const [answer] = await Promise.all([
                    uploadChunk(formData, controller.signal),
                    hasher.update(buffer)
                ]);
                if (answer.code != 0) throw new Error(answer.msg);
                checkActive();
                sentBytes += length;
                const elapsed = performance.now() - startedAt;
                const speed = elapsed > 0 ? sentBytes * 1000 / elapsed : 0;
                const speedText = speed >= 1024 * 1024
                    ? `${(speed / (1024 * 1024)).toFixed(2)} MB/s`
                    : `${(speed / 1024).toFixed(2)} KB/s`;
                onProgress(i + 1, chunks, speedText, 'uploadChunk');
            }
            checkActive();
            const md5 = await hasher.digest();
            checkActive();
            merging = true;
            onProgress(chunks, chunks, '', 'mergeChunksStart');
            const answer = await mergeChunks(md5, fileName, chunks, file.lastModified, uploadId);
            if (answer.code != 0) throw new Error(answer.msg);
            if (answer.data?.md5?.toLowerCase() !== md5.toLowerCase()) {
                throw new Error(loc['md5_error']);
            }
            completed = true;
            return { message: answer.msg, md5 };
        } catch (error) {
            if (controller.signal.aborted || error.name === 'AbortError') return { aborted: true };
            throw error;
        } finally {
            controller.abort();
            hasher?.cancel();
            if (!completed && !merging) {
                try { await abortUploadChunk(fileName, uploadId); }
                catch (error) { console.error('Upload cleanup failed', error); }
            }
            if (activeUpload === job) activeUpload = null;
        }
    };
})();
