(function() {
    window.createUploadHasher = async function(signal) {
        if (!window.Worker) {
            const spark = new SparkMD5.ArrayBuffer();
            return {
                update: async buffer => { spark.append(buffer); },
                digest: async () => spark.end(),
                cancel: () => spark.destroy()
            };
        }
        const worker = new Worker('worker-mydroid-md5.js?stream-md5=1');
        let pending = null;
        let stopped = false;
        function stop(error = new Error('MD5 canceled')) {
            if (stopped) return;
            stopped = true;
            worker.terminate();
            signal?.removeEventListener('abort', abort);
            if (pending) pending.reject(error);
            pending = null;
        }
        const abort = () => stop(new DOMException('MD5 canceled', 'AbortError'));
        if (signal?.aborted) abort();
        else signal?.addEventListener('abort', abort, { once: true });
        worker.onmessage = event => {
            if (!pending) return;
            const request = pending;
            pending = null;
            if (event.data.type === 'HASH_ERROR') {
                request.reject(new Error(event.data.message));
                stop();
            } else request.resolve(event.data);
        };
        worker.onerror = event => stop(new Error(event.message || 'MD5 worker failed'));
        worker.onmessageerror = () => stop(new Error('Cannot read MD5 worker response'));
        function request(type, buffer) {
            if (stopped) return Promise.reject(new Error('MD5 canceled'));
            if (pending) return Promise.reject(new Error('MD5 request already running'));
            return new Promise((resolve, reject) => {
                pending = { resolve, reject };
                try {
                    worker.postMessage({ type, buffer }, buffer ? [buffer] : []);
                } catch (error) { stop(error); }
            });
        }
        try {
            await request('INIT');
            return {
                update: buffer => request('UPDATE', buffer),
                digest: async () => {
                    const result = await request('FINAL');
                    if (!/^[a-f0-9]{32}$/i.test(result.hash)) throw new Error('Invalid MD5 result');
                    stop();
                    return result.hash;
                },
                cancel: () => stop()
            };
        } catch (error) {
            stop(error);
            throw error;
        }
    };
})();
