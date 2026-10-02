package com.allan.mydroid.client

import com.allan.mydroid.client.api.ClientChunkUploader
import com.allan.mydroid.client.api.ClientWsClient
import com.allan.mydroid.client.download.GlobalDownloadObj
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.koin.core.module.dsl.viewModel
import com.allan.mydroid.client.send.ConnectToHostSendViewModel
import com.allan.mydroid.client.receive.ConnectToHostReceiveViewModel
import com.allan.mydroid.client.chat.ConnectToHostChatViewModel

/**
 * client 端 Koin 模块。在 App 启动时追加到 globalModule。
 *
 * - [GlobalDownloadObj] single：按地址和端口隔离下载，跨页面持有请求并保存记录。
 * - [ClientChunkUploader] single：无状态，复用实例。
 * - [ClientWsClient] factory：每次注入新建实例，随 ViewModel onCleared 自然释放。
 */
val ClientKoinModule = module {
    singleOf(::GlobalDownloadObj)
    singleOf(::ClientChunkUploader)
    factoryOf(::ClientWsClient)
    viewModel { parameters -> ConnectToHostSendViewModel(parameters.get()) }
    viewModel { parameters -> ConnectToHostReceiveViewModel(parameters.getOrNull<HostEndpoint>()) }
    viewModel { parameters -> ConnectToHostChatViewModel(parameters.get()) }
}
