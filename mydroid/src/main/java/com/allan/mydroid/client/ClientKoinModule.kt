package com.allan.mydroid.client

import com.allan.mydroid.client.api.ClientChunkUploader
import com.allan.mydroid.client.api.ClientWsClient
import com.allan.mydroid.client.download.GlobalDownloadObj
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * client 端 Koin 模块。在 App 启动时追加到 globalModule。
 *
 * - [GlobalDownloadObj] single：跨 Fragment 生命周期持有下载状态（KV Map<ip, HostDownloadList>）。
 * - [ClientChunkUploader] single：无状态，复用实例。
 * - [ClientWsClient] factory：每次注入新建实例，随 ViewModel onCleared 自然释放。
 */
val ClientKoinModule = module {
    singleOf(::GlobalDownloadObj)
    singleOf(::ClientChunkUploader)
    factoryOf(::ClientWsClient)
}
