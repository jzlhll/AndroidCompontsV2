let hasher = null;
let useWasm = false;

// 每次只处理一个上传分块，内存随分块大小固定，不预读整个文件。
self.onmessage = async function(event) {
    const { type, buffer } = event.data;
    try {
        if (type === 'INIT') {
            try {
                self.importScripts('./hash-wasm-md5.umd.min.js');
                hasher = await self.hashwasm.createMD5();
                useWasm = true;
            } catch (error) {
                console.warn('WebAssembly MD5 unavailable, using SparkMD5', error);
                self.importScripts('./spark-md5.js');
                hasher = new self.SparkMD5.ArrayBuffer();
                useWasm = false;
            }
            self.postMessage({ type: 'READY' });
        } else if (type === 'UPDATE') {
            if (!hasher) throw new Error('MD5 is not initialized');
            if (useWasm) hasher.update(new Uint8Array(buffer));
            else hasher.append(buffer);
            self.postMessage({ type: 'UPDATED' });
        } else if (type === 'FINAL') {
            if (!hasher) throw new Error('MD5 is not initialized');
            const hash = useWasm ? hasher.digest() : hasher.end();
            self.postMessage({ type: 'HASH_RESULT', hash });
            if (!useWasm) hasher.destroy();
            hasher = null;
        }
    } catch (error) {
        self.postMessage({ type: 'HASH_ERROR', message: error.message || String(error) });
    }
};
