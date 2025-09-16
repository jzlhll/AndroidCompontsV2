(function() {
    //250MB就认为是小文件。
    const SMALL_FILE_DEFINE_SIZE = 250 * 1024 * 1024;

    let mFileSaver = null;
    const uuidDecoder = new TextDecoder();

    window.startDownloadUri = function(uriUuid) {
        const json = {};
        json.api = API_WS_REQUEST_FILE;
        json.data = {uriUuid: uriUuid};
        WS.send(JSON.stringify(json));
    }

    window.cancelDownloadUri = function(uriUuid) {
        const json = {};
        json.api = API_WS_FILE_DOWNLOAD_CANCEL;
        json.data = {uriUuid: uriUuid};
        WS.send(JSON.stringify(json));
    }

    async function handleChunk(blob) {
        const arrayBuffer = await blob.arrayBuffer();
        const dataView = new DataView(arrayBuffer);
        // 假设前 4 字节为 UUID 长度
        const uuidLen = 32; //dataView.getUint32(0, false);

        const uuid = uuidDecoder.decode(arrayBuffer.slice(0, uuidLen));
        // 后续为分片信息
        const index = dataView.getUint32(32, false);
        const totalChunks = dataView.getUint32(36, false);
        const offset = dataView.getBigUint64(40, false);
        const dataSize = dataView.getUint32(48, false);
        const data = blob.slice(52, 52 + dataSize);
        await mFileSaver?.handleChunk(uuid, index, totalChunks, offset, dataSize, data);
    }

    class AbsFileSaver {
        constructor() {}

        chunkMap = new Map();

        async onStart(uuid, fileName, totalFileSize, totalChunks) {}

        async handleChunk(uuid, index, totalChunks, offset, dataSize, data) {}

        async onStop(uuid, fileName) {}

        handleIfChunkLast(uuid, index, totalChunks, offset, dataSize) {
            if (index == totalChunks - 1) {
                const fileSize = this.chunkMap.get("totalFileSize-" + uuid);
                console.log("offset " + offset + " dataSize " + dataSize + " fileSize " + fileSize);
                if (BigInt(offset) + BigInt(dataSize) == fileSize) {
                    //正确结束
                    const fileName = this.chunkMap.get("fileName-" + uuid);
                    this.onStop(uuid, fileName);
                } else {
                    console.error("the last one is error fileSize not match???");
                }
            }
        }

        onComplete(uuid, isSuccess) {
            const json = {};
            json.api = API_WS_FILE_DOWNLOAD_COMPLETE;
            json.data = {uriUuid: uuid, isSuccess: isSuccess};
            WS.send(JSON.stringify(json));
        }

        async cancelDownload(uuid) {
            console.log(`文件 ${uuid} 取消下载`);
            if (this.chunkMap.has(uuid)) {
                this.chunkMap.delete(uuid);
                this.chunkMap.delete("fileName-" + uuid);
                this.chunkMap.delete("totalFileSize-" + uuid);
            }
            htmlDownloadCancel(uuid);
        }
    }

    class LargeFileSaver extends AbsFileSaver {
        constructor() {
            super();
        }

        async onStart(uuid, fileName, totalFileSize, totalChunks) {
            // 创建可写流
            if (this.chunkMap.has(uuid)) {
                this.chunkMap.delete(uuid);
            }

            this.chunkMap.set(uuid, StreamSaver.createWriteStream(fileName, {
                size: totalFileSize
            }));
            this.chunkMap.set("fileName-" + uuid, fileName);
            this.chunkMap.set("totalFileSize-" + uuid, totalFileSize);
            
            htmlDownloadProcess(uuid, loc["transfer_start"], false, false);
        }

        getWriter(uuid) {
            const fileStream = this.chunkMap.get(uuid);
            if (!fileStream) {
                console.error("No file Stream for " + uuid);
                return null;
            }
            const writer = fileStream.getWriter();
            if (!writer) {
                console.error("No file stream writer for " + uuid);
                return null;
            }
            return writer;
        }

        async handleChunk(uuid, index, totalChunks, offset, dataSize, data) {
            const writer = this.getWriter(uuid);
            if(!writer) return;
            try {
                await writer.write(data);
            } catch (error) {
                console.error('写入流错误:', error);
                this.onComplete(uuid, false);
                return;
            }
            
            const transferStr = loc["progress"];
            const percent = (index * 100 / totalChunks) | 0;
            htmlDownloadProcess(uuid, `${transferStr} ${percent}%`, false, true);

            this.handleIfChunkLast(uuid, index, totalChunks, offset, dataSize);
        }

        async onStop(uuid, fileName) {
            const writer = this.getWriter(uuid);
            if(!writer) return;

            htmlDownloadProcess(uuid, loc["merging"], false, false);
            console.log(`${nowTimeStr()} on Stop all is good.`);
            try {
                await writer.close();
                console.log(`文件 ${fileName} 下载完成`);
                htmlDownloadProcess(uuid, loc["download_complete"], false, false);
                this.onComplete(uuid, true);
            } catch (error) {
                console.error('合并下载失败:', error);
                this.onComplete(uuid, false);
            }

            this.chunkMap.delete(uuid);
            this.chunkMap.delete("fileName-" + uuid);
            this.chunkMap.delete("totalFileSize-" + uuid);
            console.log(`${nowTimeStr()} on Stop all is good end.`);
        }
    }

    class SmallFileSaver extends AbsFileSaver {
        constructor() {
            super();
        }

        chunkMap = new Map(); // 用于跟踪文件分片状态

        async onStart(uuid, fileName, totalFileSize, totalChunks) {
            if (this.chunkMap.has(uuid)) {
                this.chunkMap.delete(uuid);
            }
            this.chunkMap.set(uuid, new Map());
            this.chunkMap.set("fileName-" + uuid, fileName);
            this.chunkMap.set("totalFileSize-" + uuid, totalFileSize);
            htmlDownloadProcess(uuid, loc["transfer_start"], false, false);
        }

        async handleChunk(uuid, index, totalChunks, offset, dataSize, data) {
            const chunks = this.chunkMap.get(uuid);
            if (!chunks) return;

            const a = {};
            a.index = index;
            a.dataSize = dataSize;
            a.data = data;
            chunks.set(index, a);

            const transferStr = loc["progress"];
            const percent = (index * 100 / totalChunks) | 0;
            htmlDownloadProcess(uuid, `${transferStr} ${percent}%`, false, true);

            this.handleIfChunkLast(uuid, index, totalChunks, offset, dataSize);
        }

        async onStop(uuid, fileName) {
            const chunks = this.chunkMap.get(uuid);
            if (!chunks) return;

            htmlDownloadProcess(uuid, loc["merging"], false, false);
            console.log(`${nowTimeStr()} on Stop all is good.`);
            try {
                // 合并分片
                const sortedChunks = Array.from(chunks.values())
                    .sort((a, b) => a.index - b.index)
                    .map(c => c.data);
                
                const mergedBlob = new Blob(sortedChunks, { type: 'application/octet-stream' });

                // 创建下载链接
                const downloadLink = document.createElement('a');
                const objectUrl = URL.createObjectURL(mergedBlob);
                
                // 现代浏览器下载方案
                downloadLink.href = objectUrl;
                downloadLink.download = fileName;
                document.body.appendChild(downloadLink);
                downloadLink.click();

                htmlDownloadProcess(uuid, loc["download_complete"], false, false);

                // 立即清理资源
                requestAnimationFrame(() => {
                    document.body.removeChild(downloadLink);
                    URL.revokeObjectURL(objectUrl);
                });

                console.log(`文件 ${fileName} 下载完成`);

                this.onComplete(uuid, true);
            } catch (error) {
                console.error('合并下载失败:', error);
                this.onComplete(uuid, false);
            }

            this.chunkMap.delete(uuid);
            this.chunkMap.delete("fileName-" + uuid);
            this.chunkMap.delete("totalFileSize-" + uuid);
            console.log(`${nowTimeStr()} on Stop all is good end.`);
        }
    }

    window.parseMessage = async function(eventData) {
        if (eventData instanceof Blob) {
            handleChunk(eventData);
            return true;
        } else {
            const jsonData = JSON.parse(eventData);
            const data = jsonData.data;
            const api = jsonData.api;
            const msg = jsonData.msg;

            if (api == API_WS_SEND_SMALL_FILE_CHUNK || api == API_WS_SEND_FILE_CHUNK) {
                if (data.action == "start") {
                    console.log(api, msg, data);
                    if (!mFileSaver) {
                        mFileSaver = new SmallFileSaver();
                    }
                    mFileSaver.onStart(data.uriUuid, data.fileName, data.totalFileSize, data.totalChunks);
                } else if (data.action == "end") {
                    console.log(api, msg, data);
                    let fileName = data.fileName;
                    if (fileName == "") {
                        fileName = data.uriUuid;
                    }
                    mFileSaver?.onStop(data.uriUuid, fileName, data.totalFileSize, data.totalChunks);
                } else if (data.action == "cancel") {
                    console.log(api, msg, data);
                    mFileSaver?.cancelDownload(data.uriUuid);
                }
                return true;
            } else if (api == API_WS_CLIENT_INIT_CALLBACK) {
                htmlUpdateIpClient(data.myDroidMode, data.clientName, data.color);
                return true;
            } else if (api == API_WS_SEND_FILE_LIST) {
                htmlShowFileList(data.urlRealInfoHtmlList);
                return true;
            } else if (api == API_WS_SEND_FILE_NOT_EXIST || api == API_WS_SEND_NO_FILE_SIZE) {
                onStartDownErr(msg, data.uriUuid);
                return true;
            }
        }

        return false;
    }
})();