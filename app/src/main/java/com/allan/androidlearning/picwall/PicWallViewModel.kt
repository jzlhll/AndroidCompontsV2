package com.allan.androidlearning.picwall

import android.provider.MediaStore
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewModelScope
import com.allan.androidlearning.pictureselector.toPickUriWrap
import com.au.module_android.log.logdNoFile
import com.au.module_android.simpleflow.AbsActionDispatcherViewModel
import com.au.module_android.simpleflow.IStateAction
import com.au.module_android.simpleflow.createStatusStateFlow
import com.au.module_android.simpleflow.setLoading
import com.au.module_android.simpleflow.setSuccess
import com.au.module_android.utils.launchOnThread
import com.au.module_imagecompressed.PickUriWrap
import com.luck.picture.lib.basic.PictureSelector
import com.luck.picture.lib.config.SelectMimeType
import kotlinx.coroutines.flow.asStateFlow

class PicWallViewModel : AbsActionDispatcherViewModel() {
    // 定义Action类
    data class RequestLocalAction(val activity: FragmentActivity) : IStateAction
    
    // 定义状态流
    private val _localMediaState = createStatusStateFlow<List<PickUriWrap>>()
    val localMediaState = _localMediaState.asStateFlow()
    
    init {
        // 注册reducer
        getActionStore().reduce(RequestLocalAction::class.java) { action ->
            loadUriFromGallery(action.activity)
        }
    }
    
    private fun loadUriFromGallery(activity: FragmentActivity) {
        logdNoFile { "load uri from gallery" }
        _localMediaState.setLoading()
        
        PictureSelector.create(activity)
            .dataSource(SelectMimeType.ofImage())
            .setMaxSelectNum(Int.MAX_VALUE)
            .isPageStrategy(false)
            .setQuerySortOrder(MediaStore.MediaColumns.DATE_MODIFIED + " DESC")
            .obtainMediaData { result ->
                logdNoFile { "onComplete: ${result.size}" }
                val convertedList = result.map { it.toPickUriWrap(result.size) }
                _localMediaState.setSuccess(convertedList)
            }
    }
}