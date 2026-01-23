package com.allan.androidlearning.picwall

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import com.allan.classnameanno.EntryFrgName
import com.au.module_android.log.logdNoFile
import com.au.module_android.simpleflow.collectStatusState
import com.au.module_android.utils.changeBarsColor
import com.au.module_android.utils.launchRepeatOnStarted
import com.au.module_androidui.ui.base.ImmersiveMode
import com.au.module_androidui.ui.views.ViewFragment

@EntryFrgName(backgroundColor = "#ff00ff")
class PicWallFragment : ViewFragment() {
    private val mViewModel by viewModels<PicWallViewModel>()
    
    override fun immersiveMode(): ImmersiveMode {
        return ImmersiveMode.FullImmersive()
    }

    override fun onUiCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        requireActivity().changeBarsColor(false, false)

        val view = InfiniteCanvasView(inflater.context)

        // 收集状态
        launchRepeatOnStarted {
            mViewModel.localMediaState.collectStatusState(
                onSuccess = { data ->
                    // TODO: 处理成功数据
                    logdNoFile { "view model loaded data.size: ${data.size}" }
                    view.setFrameImageList(data)
                }
            )
        }

        // 触发本地图片加载
        mViewModel.dispatch(PicWallViewModel.RequestLocalAction(requireActivity()))

        return view
    }


}