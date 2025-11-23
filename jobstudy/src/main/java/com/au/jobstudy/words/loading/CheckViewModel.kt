package com.au.jobstudy.words.loading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.au.jobstudy.words.domain.beans.DBTableMode
import com.au.jobstudy.words.data.WordsRepositoryImpl
import com.au.module_android.simpleflow.ActionDispatcherImpl
import com.au.module_android.simpleflow.IActionDispatcher
import com.au.module_android.simpleflow.IStateAction
import com.au.module_android.simpleflow.StatusState
import com.au.module_android.utils.launchOnThread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CheckViewModel : ViewModel(), IActionDispatcher by ActionDispatcherImpl() {
    class LoadingAction(val dbTabMode: DBTableMode,
                        val sheetName:String? = null) : IStateAction

    class UIInfo {
        var currentIndex = 0
        var totalWords = 0

    }

    private val infoBeans = java.util.LinkedList<UIInfo>()

    private val _currentFlow = MutableStateFlow<StatusState<UIInfo>>(StatusState.Loading)
    val currentFlow: StateFlow<StatusState<UIInfo>> = _currentFlow.asStateFlow()

    private var isPlayingSentence = false
    fun resetTo(index:Int) {
        isPlayingSentence = false
    }

    init {
        getActionStore().apply {
            reduce(LoadingAction::class.java) {
                startLoad(it)
            }
        }
    }

    private fun startLoad(action: LoadingAction) {
        val sheetName = action.sheetName
        val dbTabMode = action.dbTabMode

        val repo = WordsRepositoryImpl()

        viewModelScope.launchOnThread {
            when (dbTabMode) {
                DBTableMode.Word -> {
                    val words = repo.getWord(sheetName)
                    infoBeans.add(UIInfo().apply {
                        totalWords = words.size
                    })
                }
            }
        }
    }
}