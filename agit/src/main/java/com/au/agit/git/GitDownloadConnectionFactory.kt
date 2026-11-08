package com.au.agit.git

import org.eclipse.jgit.transport.http.HttpConnection
import org.eclipse.jgit.transport.http.JDKHttpConnection
import org.eclipse.jgit.transport.http.JDKHttpConnectionFactory
import java.io.FilterInputStream
import java.io.InputStream
import java.net.Proxy
import java.net.URL

/** 累计 Git 下载响应中实际读取的字节，沿用 JGit 的连接和 TLS 配置。 */
class GitDownloadConnectionFactory(private val onBytesRead: (Long) -> Unit) : JDKHttpConnectionFactory() {
    override fun create(url: URL): HttpConnection = DownloadConnection(url, onBytesRead)

    override fun create(url: URL, proxy: Proxy): HttpConnection = DownloadConnection(url, proxy, onBytesRead)

    private class DownloadConnection : JDKHttpConnection {
        private val onBytesRead: (Long) -> Unit

        constructor(url: URL, onBytesRead: (Long) -> Unit) : super(url) {
            this.onBytesRead = onBytesRead
        }

        constructor(url: URL, proxy: Proxy, onBytesRead: (Long) -> Unit) : super(url, proxy) {
            this.onBytesRead = onBytesRead
        }

        override fun getInputStream(): InputStream {
            val input = super.getInputStream()
            if (!getURL().path.endsWith("/git-upload-pack")) return input
            return object : FilterInputStream(input) {
                override fun read(): Int {
                    val value = `in`.read()
                    if (value >= 0) onBytesRead(1)
                    return value
                }

                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    val count = `in`.read(buffer, offset, length)
                    if (count > 0) onBytesRead(count.toLong())
                    return count
                }
            }
        }
    }
}
