package com.allan.androidlearning.activities2

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.allan.classnameanno.EntryFrgName
import com.au.module_androidui.ui.views.ViewToolbarFragment
import com.au.module_androidui.ui.views.YourToolbarInfo
import com.au.module_android.log.logt
import com.au.module_android.utils.unsafeLazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.getValue

@EntryFrgName()
class KotlinChannelStudyFragment : ViewToolbarFragment() {
    //private val mViewModel : ChannelViewModel1 by viewModels()
    private val mViewModel3 by unsafeLazy { ViewModelProvider(this)[ChannelViewModel3::class.java] }

    override fun toolbarInfo(): YourToolbarInfo? {
        return YourToolbarInfo.Defaults("ChannelStudy")
    }

    override fun onUiCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        init1()  //注册早于
        return LinearLayout(inflater.context)
    }

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private fun init1() {
// 方式一：scope.launch + collect
//        scope.launch {
//            flow {
//                emit(1)
//                emit(2)
//                throw RuntimeException("Flow construction error!")
//            }.collect { value ->
//                logt { ("Collect received: $value") }
//                if (value == 1) {
//                    throw RuntimeException("Processing error in collect!")
//                }
//            }
//        }

// 方式二：onEach + launchIn（无catch）
        lifecycleScope.launch {
            flowOnExample()
        }
    }

    suspend fun flowOnExample() {
        // flowOn 影响上游操作的上下文
        flow {
            // 这个块在 IO 线程执行
            logt { ("发射线程: ${Thread.currentThread().name}") }
            emit(1)
            emit(2)
        }.map {  n->
            logt { ("发射线程2: ${Thread.currentThread().name}") }
            "" + n
        }.flowOn(Dispatchers.Main) // 指定上游上下文
            .map {
                // 这个map在默认上下文执行（因为后面没有flowOn）
                logt { "映射线程: ${Thread.currentThread().name}"}
                it.toInt() * 2
            }
            .flowOn(Dispatchers.Default) // 可以再次改变上游上下文
            .collect {
                // 收集在调用协程的上下文
                logt { "收集线程: ${Thread.currentThread().name}, 值: $it"}
            }
    }
}

class ChannelViewModel1 : ViewModel() {
    val channel = Channel<Int>(Channel.UNLIMITED)

    fun simpleFlow(): Flow<Int> = flow {
        for (i in 1..3) {
            delay(100) // 可以调用挂起函数
            emit(i)    // 发射值
        }
    }

    fun createChannelFlow() = channelFlow<String> {

    }.buffer(20)

    fun channelStart() {
        viewModelScope.launch {
            for (i in 1..5) {
                logt { "channel 发送1: $i" }
                delay(1000)
                logt { "channel 发送2: $i" }
                channel.send(i) // 挂起函数
                //channel.trySend(100) // 挂起函数。无需在协程块中调用
            }
            logt { "close channel." }
            channel.close() // 关闭channel
        }
    }
}

class ChannelViewModel2 : ViewModel() {
    val channel = Channel<Int>(2)
    suspend fun sendOperations() {
        // 基本发送
        channel.send(1)
        // trySend - 非挂起版本
        val result = channel.trySend(2)
        when {
            result.isSuccess -> println("发送成功")
            result.isClosed -> println("Channel已关闭")
            result.isFailure -> println("发送失败")
        }

        channel.close()
    }
}

class ChannelViewModel3 : ViewModel() {
    val numberFlow = (1..5).asFlow()
}