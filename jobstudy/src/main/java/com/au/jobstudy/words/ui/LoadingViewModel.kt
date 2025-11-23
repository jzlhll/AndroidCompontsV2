package com.au.jobstudy.words.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.au.jobstudy.words.data.ImportExcelRepositoryImpl
import com.au.jobstudy.words.data.WordsRepositoryImpl
import com.au.jobstudy.words.usecase.LoadingUseCase
import com.au.module_android.utils.launchOnThread
import com.au.module_android.utils.logd
import com.au.module_android.utils.unsafeLazy
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class LoadingViewModel : ViewModel() {
    private val TAG = "LoadingViewModel"

    // 设置replay=1确保即使在emit后collect也能收到最新的事件
    private val _overFlow = MutableSharedFlow<Boolean>()
    val overFlow = _overFlow.asSharedFlow()

    val loadingUseCase by unsafeLazy { LoadingUseCase(ImportExcelRepositoryImpl(), WordsRepositoryImpl()) }

    fun checkAndImportExcel() {
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