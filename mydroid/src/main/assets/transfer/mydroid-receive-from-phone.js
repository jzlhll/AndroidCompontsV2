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

    window.downloadUriComplete = function(uriUuid) {
        const json = {};
        json.api = API_WS_FILE_DOWNLOAD_COMPLETE;
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
        const total = dataView.getUint32(36, false);
        const offset = dataView.getBigUint64(40, false);
        const dataSize = dataView.getUint32(48, false);
        const data = blob.slice(52, 52 + dataSize);
        //console.log(`handle Chunk ${uuid} index:${index}/${total} offset:${offset} dataSize:${dataSize}`);

        await mFileSaver?.handleChunk(uuid, index, total, offset, dataSize, data);
    }

    class AbsFileSaver {
        constructor() {
        }
        
        async onStart(uuid, totalFileSize, totalChunks) {
        }

        async onStop(uuid, fileName, totalFileSize, totalChunks) {
        }

        async handleChunk(uuid, index, total, offset, dataSize, data) {
        }

        async cancelDownload(uuid) {
        }
    }

    class LargeFileSaver extends AbsFileSaver {
        constructor() {
            super();
        }

        fileStreamMap = new Map();

        async onStart(uuid, totalFileSize, totalChunks) {
            // 创建可写流
            if (this.fileStreamMap.has(uuid)) {
                this.fileStreamMap.delete(uuid);
            }
            const fileStream = StreamSaver.createWriteStream(uuid, {
                size: totalFileSize
            });
            this.fileStreamMap.set(uuid, fileStream);
            htmlDownloadProcess(uuid, loc["transfer_start"], false, false);
        }

        async handleChunk(uuid, index, total, offset, dataSize, data) {
            const fileStream = this.fileStreamMap.get(uuid);
            if (!fileStream) {
                console.error("No file Stream for " + uuid);
                return;
            }

            try {
                await fileStream.getWriter().write(data);
            } catch (error) {
                console.error('写入流错误:', error);
                this.cancelDownload(uuid);
            }
            
            let percent = 0;
            const transferStr = loc["progress"];
            if (total > 0) {
                percent = (index * 100 / total) | 0;
                htmlDownloadProcess(uuid, `${transferStr} ${percent}%`, false, true);
            } else {
                const sz = myDroidFormatSize(offset);
                htmlDownloadProcess(uuid, `${transferStr} ${sz}`, false, true);
            }
        }

        async onStop(uuid, fileName, totalFileSize, totalChunks) {
            const chunks = this.chunkMap.get(uuid);
            if (!chunks) return;

            htmlDownloadProcess(uuid, loc["merging"], false, false);
            let checkCount = 5;
            if (checkCount > 0) {
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
                } catch (error) {
                    console.error('合并下载失败:', error);
                }
                
                this.chunkMap.delete(uuid);
            } else {
                console.log(`${nowTimeStr()} on Stop is bad.`);
                this.chunkMap.delete(uuid);
            }
        }

        async cancelDownload(uuid) {
        }
    }

    class SmallFileSaver extends AbsFileSaver {
        constructor() {
            super();
        }

        chunkMap = new Map(); // 用于跟踪文件分片状态

        async onStart(uuid, totalFileSize, totalChunks) {
            if (this.chunkMap.has(uuid)) {
                this.chunkMap.delete(uuid);
            }
            this.chunkMap.set(uuid, new Map());
            htmlDownloadProcess(uuid, loc["transfer_start"], false, false);
        }

        async handleChunk(uuid, index, total, offset, dataSize, data) {
            const chunks = this.chunkMap.get(uuid);
            const a = {};
            a.index = index;
            a.dataSize = dataSize;
            a.data = data;
            chunks.set(index, a);
            let percent = 0;
            const transferStr = loc["progress"];
            if (total > 0) {
                percent = (index * 100 / total) | 0;
                htmlDownloadProcess(uuid, `${transferStr} ${percent}%`, false, true);
            } else {
                const sz = myDroidFormatSize(offset);
                htmlDownloadProcess(uuid, `${transferStr} ${sz}`, false, true);
            }
        }

        // checkCompletionOnce(uuid, totalChunks) {
        //     const chunks = this.chunkMap.get(uuid);
        //     if (!chunks) return false;

        //     let i = 0;
        //     while (i < totalChunks) {
        //         if (!chunks.get(i++)) {
        //             return false;
        //         }
        //     }
        //     return true;
        // }

        async onStop(uuid, fileName, totalFileSize, totalChunks) {
            const chunks = this.chunkMap.get(uuid);
            if (!chunks) return;

            htmlDownloadProcess(uuid, loc["merging"], false, false);
            let checkCount = 5;
            // while (checkCount-- >= 0) {
            //     const isCompleted = this.checkCompletionOnce(uuid, totalChunks);
            //     if (isCompleted) {
            //         break;
            //     }
            //     await delay(500);
            // }
            if (checkCount > 0) {
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
                } catch (error) {
                    console.error('合并下载失败:', error);
                }
                
                this.chunkMap.delete(uuid);
            } else {
                console.log(`${nowTimeStr()} on Stop is bad.`);
                this.chunkMap.delete(uuid);
            }
        }

        async cancelDownload(uuid) {
            console.log(`文件 ${uuid} 取消下载`);
            if (this.chunkMap.has(uuid)) {
                this.chunkMap.delete(uuid);
            }
            htmlDownloadCancel(uuid);
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
                    mFileSaver.onStart(data.uriUuid, data.totalFileSize, data.totalChunks);
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
            } else if (api == API_WS_SEND_FILE_NOT_EXIST) {
                onStartDownErr(msg, data.uriUuid);
                return true;
            }
        }

        return false;
    }
})();