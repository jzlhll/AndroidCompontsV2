package com.au.jobstudy.words.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.au.jobstudy.words.usecase.LoadingUseCase
import com.au.module_android.log.logd
import com.au.module_android.log.logdNoFile
import com.au.module_android.utils.launchOnThread
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class LoadingViewModel(
    private val loadingUseCase : LoadingUseCase,
//    private val loadingTest : LoadingTest
) : ViewModel() {

    private val TAG = "LoadingViewModel"

    // 设置replay=1确保即使在emit后collect也能收到最新的事件
    private val _overFlow = MutableSharedFlow<Boolean>()
    val overFlow = _overFlow.asSharedFlow()

    fun checkAndImportExcel() {
        logdNoFile { "loadingTest LoadingViewModel 11" }
//        loadingTest.add()
        viewModelScope.launchOnThread {
            logd(TAG) { "check & ImportExcel" }
            loadingUseCase.checkAndImportExcel()
            _overFlow.emit(true)
        }
    }

    override fun onCleared() {
        super.onCleared()
        loadingUseCase.close()
    }
}