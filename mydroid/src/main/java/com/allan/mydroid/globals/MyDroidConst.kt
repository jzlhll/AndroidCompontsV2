package com.allan.mydroid.globals

import com.allan.mydroid.api.MyDroidMode
import com.allan.mydroid.beansinner.ReceivingFileInfo
import com.allan.mydroid.beansinner.WebSocketClientInfo
import com.allan.mydroid.globals.NetworkObserverObj.NetworkStatus
import com.au.module_android.simplelivedata.NoStickLiveData
import java.io.File

object MyDroidConst {
    val networkStatusData: NoStickLiveData<NetworkStatus> = NoStickLiveData(NetworkStatus.Uninitialized)

    var serverIsOpen = false
    var currentDroidMode = MyDroidMode.None

    /**
     * 用于通知界面更新。告知有多少通过WS接入的client。
     */
    val clientListLiveData = NoStickLiveData<List<WebSocketClientInfo>>()

    /**
     * 文件合并成功的通知。必须使用observerNoStick。
     */
    val onFileMergedData = NoStickLiveData<File>()

    /**
     * 接收模式下的接收文件的进度
     * Map<filename-md5, ReceivingFileInfo>
     */
    val receiverProgressData = NoStickLiveData<Map<String, ReceivingFileInfo>>()

    val aliveStoppedData = NoStickLiveData<Unit>()
}