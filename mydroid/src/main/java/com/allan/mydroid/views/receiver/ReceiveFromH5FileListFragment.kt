package com.allan.mydroid.views.receiver

import android.os.Bundle
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.allan.mydroid.R
import com.allan.mydroid.databinding.FragmentMyDroidReceiveListBinding
import com.allan.mydroid.repository.GlobalFileListRepoObj
import com.allan.mydroid.state.GlobalReceiverFlowsObj
import com.allan.mydroid.views.send.SendListSelectorFragment
import com.au.module_android.Globals
import com.au.module_android.simpleflow.collectStatusState
import com.au.module_android.utils.dp
import com.au.module_android.utils.launchOnUi
import com.au.module_android.utils.launchRepeatOnStarted
import com.au.module_androidui.toast.MyToastUtil
import com.au.module_androidui.ui.bindings.BindingFragment
import com.au.module_nested.decoration.VertPaddingItemDecoration
import org.koin.android.ext.android.inject
import com.au.module_androidui.R as AndroidUiR

class ReceiveFromH5FileListFragment : BindingFragment<FragmentMyDroidReceiveListBinding>() {
    override var customBackActionEnable = false

    private val receiverFlowsObj: GlobalReceiverFlowsObj by inject()
    private val fileListRepository: GlobalFileListRepoObj by inject()
    private val mViewModel: ReceiveFromH5ViewModel by viewModels(ownerProducer = { requireParentFragment() })

    private val mAdapter = ReceiveFromH5Adapter(fullClick = { bean ->
        ReceiveFromH5FileDetailDialog.pop(
            childFragmentManager,
            arrayOf(
                bean.file.name,
                bean.md5,
                bean.fileSizeInfo
            )
        )
    }, click = {
        ReceiveHolderActionDialog.pop(
            childFragmentManager,
            it,
            fileExportFailCallback = fileExportFailCallback,
            fileExportSuccessCallback = fileExportSuccessCallback,
            refreshFileListCallback = refreshFileListCallback,
            importSendCallback = importSendCallback
        )
    }).apply {
        stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    val importSendCallback:()->Unit = {
        val navController = findNavController()
        navController.popBackStack(R.id.myDroidAllFragment, false)
        navController.navigate(
            R.id.sendListSelectorFragment,
            Bundle().apply {
                putBoolean(SendListSelectorFragment.KEY_AUTO_ENTER_SEND_VIEW, true)
            },
            NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setEnterAnim(com.au.module_androidui.R.anim.activity_open_enter)
                .setExitAnim(com.au.module_androidui.R.anim.activity_open_exit)
                .setPopEnterAnim(com.au.module_androidui.R.anim.activity_close_enter)
                .setPopExitAnim(com.au.module_androidui.R.anim.activity_close_exit)
                .build(),
        )
    }

    val refreshFileListCallback = {
        mViewModel.dispatch(ReceiveFromH5ViewModel.LoadFileListAction())
    }

    val fileExportFailCallback:(String)->Unit = { info->
        Globals.mainScope.launchOnUi {
            MyToastUtil.toastOnTopError(info)
        }
    }

    val fileExportSuccessCallback:(info:String, exportFileStr:String)->Unit = { info, exportFileStr->
        Globals.mainScope.launchOnUi {
            MyToastUtil.toastOnBottom(
                info.replace("/storage/emulated/0/", "/sdcard/"),
                iconResId = AndroidUiR.drawable.ic_successful,
            )
            mViewModel.dispatch(ReceiveFromH5ViewModel.WriteHistoryAction(exportFileStr))
        }
    }

    override fun onBindingCreated(savedInstanceState: Bundle?) {
        binding.receiveRcv.apply {
            adapter = mAdapter
            layoutManager = LinearLayoutManager(context)
            setHasFixedSize(true)
            addItemDecoration(VertPaddingItemDecoration(0, 0, 2.dp))
        }

        launchRepeatOnStarted {
            receiverFlowsObj.fileMergedFlow.collect { file->
                MyToastUtil.toastOnBottom(
                    Globals.app.getString(R.string.file_received_success_fmt, file.name),
                    iconResId = AndroidUiR.drawable.ic_successful,
                )
            }
        }

        launchRepeatOnStarted {
            fileListRepository.fileListStateFlow.collectStatusState(onSuccess = { fileList ->
                mAdapter.submitList(fileList, false)
                binding.receiveRcvEmptyTv.isVisible = fileList.isEmpty()
            })
        }

        refreshFileListCallback()
    }
}
