package com.allan.androidlearning.activities2

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.allan.classnameanno.EntryFrgName
import com.au.module_android.click.onClick
import com.au.module_android.simpleflow.ActionDispatcherImpl
import com.au.module_android.simpleflow.IActionDispatcher
import com.au.module_android.simpleflow.IStateAction
import com.au.module_android.simpleflow.StatusState
import com.au.module_android.simpleflow.collectStatusState
import com.au.module_android.ui.views.ViewFragment
import com.au.module_android.utils.logt
import com.au.module_android.widget.CustomFontText
import com.au.module_androidui.toast.ToastBuilder
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

//        android时下的开发模板UI层+viewModel+DataSourceRepository。
//        开发建议：
//        1. 不要直接把repository对象在UI层调用，必须放在ViewModel中去持有；
//        2.（重点）repository数据源，函数只提供suspend函数，“不要跟scope扯上关系”。为什么呢，scope应该由调用者viewModel/UI的lifecycleScope来管控，数据源头产生者不应该掐住数据产生的咽喉。
//        尤其是在scope的限定作用域下执行文件传输，取消等危险动作，将会导致传输会生命周期的影响而莫名停止的风险。
//
//        3. 虽然google推荐，越下层（即数据源头）使用flow越好。
//        但是个人经验在多人合作开发中，为了团队成员都能很好的阅读和理解代码逻辑：
//        repository数据源，个人不太建议flow，因为它的进一步源头是比如okhttp的回调式代码，数据库查询等回调式动作，不做flow提供，透传逻辑更清晰。
//
//        通常情况的，每一条数据都必须让UI层（或者说，UI只关注最新状态的情况下）是统一交到viewModel里面申明的StateFlow/SharedFlow更新即可。
//
//        特定情况：如果有急需flow的特性，比如过滤，整合其他状态，可以使用。 但我个人推荐在ViewModel去做这个事情。这种情况不做要求。但是要注意条理性。
//
//
//
//        因此我推荐的开发方式：
//
//        数据层：一般起名字叫做xxxRepository。 通常不要写flow，提供回调接口，给到ViewModel。 viewModel中对于MutableStateFlow、MutableSharedFlow做剪枝，整合，过滤，map和生命周期管理等操作。
//        ViewModel层：android的ViewModel作为UI层和数据层的中介， 里面编写SharedFlow/StateFlow来持有数据类。 并且，管理多个StateFlow的整合，过滤，映射，生命周期等。
//        UI层：fragment/activity等，不跟数据层产生任何交互，只监听ViewModel的stateFlow/SharedFlow变更UI状态。

val dataSourceScope = CoroutineScope( MainScope().coroutineContext + Dispatchers.Default )
@EntryFrgName(priority = 100)
class FlowStudyFragment : ViewFragment() {
    val viewModel by lazy { FlowStudyViewModel() }

    private val noteTransferRepository
            = DataSourceRepository(dataSourceScope)

    private lateinit var showInfoTv : CustomFontText

    override fun onUiCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                noteTransferRepository.uiState.onEach { state->
                    logt{"UIUpdate>>>: $state"}
                }.launchIn(this)
            }
        }

        return LinearLayout(requireActivity()).also { ll->
            ll.orientation = LinearLayout.VERTICAL

            ll.addView(CustomFontText(requireActivity()).also {
                showInfoTv = it
            })
            ll.addView(MaterialButton(requireActivity()).also {
                it.text = "test1"
                it.onClick {
                    noteTransferRepository.mockup()
                }
            })
        }
    }
}


sealed class TransferUiState {
    data object Idle : TransferUiState()
    data class PreImport(val size: Int) : TransferUiState()
    data object Waiting : TransferUiState()
    data class Importing(val count: Int, val size: Int) : TransferUiState()

    data class Paused(val count: Int, val size: Int) : TransferUiState()

    data class Finished(val size: Int) : TransferUiState()

    data object Connecting : TransferUiState()
    data class ImportError(val error: Throwable, val count: Int = 0, val size: Int = 0) :
        TransferUiState()

}


sealed class State {
    data object Idle : State()
    data object Preparing : State()
    data object ConnectingP2p : State()
    data object ConnectingSocket : State()
    data object Connected : State()
    data object JmdnsConnect : State()
}

class FlowStudyViewModel : ViewModel() {

}

class DataSourceRepository(private val scope: CoroutineScope) {
    private val _transferState =
        MutableStateFlow<State>(State.Idle)
    val transferState: StateFlow<State> = _transferState.asStateFlow()

    private val _uiState = MutableStateFlow<TransferUiState>(TransferUiState.Idle)

    //调试1： stateIn
    val uiState: StateFlow<TransferUiState> = _uiState.combine(transferState) { ui, transfer ->
        val result = if (transfer is State.Preparing
            || transfer is State.ConnectingP2p
            || transfer is State.ConnectingSocket
        ) {
            TransferUiState.Connecting
        } else {
            ui
        }
        result.apply {
            logt{"result ui state : $this"}
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(), _uiState.value)


    fun mockup() {
        scope.launch {
            _uiState.value = TransferUiState.Idle
            logt { "set to idle" }
            delay(5000)
            logt { "delay5s to connecting" }
            _uiState.value = TransferUiState.Connecting
            delay(2000)
            logt { "please pause app!" }

            delay(3000)
            if (true) {
                logt { "test1: before resume app setValue!" }
                _uiState.value = TransferUiState.Idle
            } else {
                logt { "test1: after resume app setValue! resume your app here" }
                delay(3000)
                logt { "test1: after resume set to idle" }
                _uiState.value = TransferUiState.Idle
            }
        }

        scope.launch {
            _uiState.value = TransferUiState.Idle
            logt { "set to idle" }
            delay(5000)
            logt { "delay5s to connecting" }
            _uiState.value = TransferUiState.Connecting
            delay(2000)
            logt { "please pause app!" }

            delay(3000)
            if (true) {
                logt { "test1: before resume app setValue!" }
                _uiState.value = TransferUiState.Idle
            } else {
                logt { "test1: after resume app setValue! resume your app here" }
                delay(3000)
                logt { "test1: after resume set to idle" }
                _uiState.value = TransferUiState.Idle
            }
        }
    }
}