package com.au.jobstudy.words.loading

import androidx.lifecycle.ViewModel
import com.au.jobstudy.words.WordsManager
import com.au.module_android.simpleflow.ActionDispatcherImpl
import com.au.module_android.simpleflow.IActionDispatcher
import com.au.module_android.simpleflow.IStateAction
import com.au.module_android.simpleflow.StatusState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CheckViewModel : ViewModel(), IActionDispatcher by ActionDispatcherImpl() {
    class LoadingAction(val dbTab:String, val word:String) : IStateAction


    class InfoBean {
        var currentIndex = 0
        var totalWords = 0
    }

    private val infoBeans = java.util.LinkedList<InfoBean>()

    private val _currentFlow = MutableStateFlow<StatusState<InfoBean>>(StatusState.Loading)
    val currentFlow: StateFlow<StatusState<InfoBean>> = _currentFlow.asStateFlow()

    private var isPlayingSentence = false
    fun resetTo(index:Int) {
        isPlayingSentence = false
    }

    init {
        getActionStore().apply {
            reduce(LoadingAction::class.java) {
                startLoad()
            }
        }
    }

    private fun startLoad() {
    }
}