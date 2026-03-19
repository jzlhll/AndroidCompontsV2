package com.allan.androidlearning.activities2

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.allan.classnameanno.EntryFrgName
import com.au.module_android.log.logd
import com.au.module_android.log.logt
import com.au.module_androidui.ui.views.ViewToolbarFragment
import com.au.module_androidui.ui.views.YourToolbarInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@EntryFrgName()
class ChannelStudyFragment : ViewToolbarFragment() {
    private val mViewModel by viewModels<ChannelExampleViewModel>()

    override fun toolbarInfo(): YourToolbarInfo? {
        return YourToolbarInfo.Defaults("ChannelStudy")
    }

    override fun onUiCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        logd { "ChannelStudyFragment on create" }
        initChannelExamples()
        return LinearLayout(inflater.context)
    }

    private fun initChannelExamples() {
        // 基本通道操作
        lifecycleScope.launch {
            mViewModel.basicChannelOperation()
        }

        // 通道与Flow的转换
        lifecycleScope.launch {
            mViewModel.channelToFlowExample()
        }

        // 缓冲通道
        lifecycleScope.launch {
            mViewModel.bufferedChannelExample()
        }

        // 取消通道
        lifecycleScope.launch {
            mViewModel.cancelChannelExample()
        }
    }
}

class ChannelExampleViewModel : ViewModel() {
    // 基本通道操作示例
    suspend fun basicChannelOperation() {
        val channel = Channel<Int>()

        // 发送协程
        viewModelScope.launch {
            for (i in 1..5) {
                logt { "Sending: $i" }
                channel.send(i) // 挂起直到有接收者
                delay(500)
            }
            channel.close() // 关闭通道
            logt { "Channel closed" }
        }

        // 接收协程
        viewModelScope.launch {
            for (value in channel) {
                logt { "Received: $value" }
            }
            logt { "Receiver done" }
        }
    }

    // 通道与Flow的转换示例
    suspend fun channelToFlowExample() {
        val flow = channelFlow {
            for (i in 1..3) {
                delay(300)
                send(i)
            }
        }

        flow.collect {
            logt { "Flow collected: $it" }
        }
    }

    // 缓冲通道示例
    suspend fun bufferedChannelExample() {
        // 创建带有缓冲容量的通道
        val bufferedChannel = Channel<Int>(3) // 缓冲容量为3

        // 发送协程 - 快速发送
        viewModelScope.launch {
            for (i in 1..10) {
                logt { "Buffered sending: $i" }
                bufferedChannel.send(i)
                logt { "Buffered sent: $i" }
                delay(100) // 快速发送
            }
            bufferedChannel.close()
        }

        // 接收协程 - 慢速接收
        viewModelScope.launch {
            for (value in bufferedChannel) {
                logt { "Buffered received: $value" }
                delay(500) // 慢速接收
            }
            logt { "Buffered receiver done" }
        }
    }

    // 取消通道示例
    suspend fun cancelChannelExample() {
        val channel = Channel<Int>()

        val senderJob = viewModelScope.launch {
            try {
                for (i in 1..10) {
                    logt { "Cancellable sending: $i" }
                    channel.send(i)
                    delay(200)
                }
            } catch (e: Exception) {
                logt { "Sender cancelled: ${e.message}" }
            }
        }

        val receiverJob = viewModelScope.launch {
            delay(1000) // 接收一段时间后取消
            logt { "Cancelling receiver" }
            channel.close() // 关闭通道
        }

        receiverJob.join()
        senderJob.join()
        logt { "Cancel example done" }
    }
}