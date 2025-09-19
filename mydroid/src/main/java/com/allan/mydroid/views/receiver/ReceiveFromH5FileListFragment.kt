package com.allan.mydroid.views.receiver

import android.os.Bundle
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.allan.mydroid.R
import com.allan.mydroid.databinding.FragmentMyDroidReceiveListBinding
import com.allan.mydroid.globals.KEY_AUTO_ENTER_SEND_VIEW
import com.allan.mydroid.globals.MyDroidConst
import com.allan.mydroid.views.send.SendListSelectorFragment
import com.au.module_android.Globals
import com.au.module_android.Globals.resStr
import com.au.module_android.click.onClick
import com.au.module_android.simpleflow.collectStatusState
import com.au.module_android.ui.FragmentShellActivity
import com.au.module_android.ui.bindings.BindingFragment
import com.au.module_android.ui.views.ToolbarInfo
import com.au.module_android.utils.asOrNull
import com.au.module_android.utils.dp
import com.au.module_android.utils.gone
import com.au.module_android.utils.launchOnUi
import com.au.module_android.utils.unsafeLazy
import com.au.module_android.utils.visible
import com.au.module_androidui.toast.ToastBuilder
import com.au.module_nested.decoration.VertPaddingItemDecoration
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch
import kotlin.getValue

class ReceiveFromH5FileListFragment : BindingFragment<FragmentMyDroidReceiveListBinding>() {
    lateinit var receivedFileListTab: TabLayout.Tab
    lateinit var exportHistoryTab: TabLayout.Tab

    private val mViewModel by unsafeLazy { ViewModelProvider(this)[ReceiveFromH5ViewModel::class.java] }

    private val isActivityMode by unsafeLazy { arguments?.getBoolean("isActivityMode") ?: false }

    val mAdapter = ReceiveFromH5Adapter(fullClick = { bean ->
        ReceiveFromH5FileDetailDialog.pop(
            childFragmentManager,
            arrayOf(
                bean.file.name,
                bean.md5,
                bean.fileSizeInfo
            )
        )
    }) {
        ReceiveHolderActionDialog.pop(
            childFragmentManager,
            it,
            fileExportFailCallback = fileExportFailCallback,
            fileExportSuccessCallback = fileExportSuccessCallback,
            refreshFileListCallback = refreshFileListCallback,
            importSendCallback = importSendCallback
        )
    }

    override fun toolbarInfo(): ToolbarInfo? {
        return if (isActivityMode) {
            ToolbarInfo(getString(R.string.transfer_list),
                true)
        } else {
            null
        }
    }

    val importSendCallback:()->Unit = {
        activity?.let { a->
            a.finishAfterTransition()
            FragmentShellActivity.Companion.start(
                a, SendListSelectorFragment::class.java,
                bundleOf(KEY_AUTO_ENTER_SEND_VIEW to true)
            )
        }
    }

    val refreshFileListCallback = {
        mViewModel.dispatch(ReceiveFromH5ViewModel.LoadFileListAction())
    }

    val fileExportFailCallback:(String)->Unit = { info->
        Globals.mainScope.launchOnUi {
            ToastBuilder().setOnTop().setMessage(info).setIcon("error").toast()
        }
    }

    val fileExportSuccessCallback:(info:String, exportFileStr:String)->Unit = { info, exportFileStr->
        Globals.mainScope.launchOnUi {
            ToastBuilder().setOnTop().setMessage(info.replace("/storage/emulated/0/", "/sdcard/"))
                .setIcon("success").toast()
            mViewModel.dispatch(ReceiveFromH5ViewModel.WriteHistoryAction(exportFileStr))
        }
    }

    override fun onBindingCreated(savedInstanceState: Bundle?) {
        binding.tabLayout.apply {
            tabSelectTextColor = R.color.logic_receiver
            tabNotSelectColor = com.au.module_androidcolor.R.color.color_text_desc
        }

        MyDroidConst.onFileMergedData.observeUnStick(this) { file->
            val strFmt = getString(R.string.file_received_success_fmt)
            ToastBuilder().setOnTop()
                .setMessage(String.format(strFmt, file.name))
                .setIcon("success").toast()

            mViewModel.dispatch(ReceiveFromH5ViewModel.LoadFileListAction())
        }

        initTabs()
        initRcv()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mViewModel.historyState.collectStatusState(
                    success = {
                        val history = R.string.keep_recent_records.resStr() + "\n\n" + it
                        binding.exportHistoryTv.text = history
                        updateTabsTitle(false)
                    },
                    error = {
                    }
                )
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mViewModel.fileListState.collectStatusState(
                    success = { fileList->
                        mAdapter.submitList(fileList, false)
                        changeRcvEmptyTextVisible()
                        updateTabsTitle(true)
                    },
                    error = {
                    }
                )
            }
        }

        mViewModel.dispatch(ReceiveFromH5ViewModel.LoadFileListAction())
        mViewModel.dispatch(ReceiveFromH5ViewModel.LoadHistoryAction())
    }

    private fun updateTabsTitle(updateFileList:Boolean) {
        val fileListTabTv = receivedFileListTab.customView.asOrNull<TextView>()
        val historyTabTv = exportHistoryTab.customView.asOrNull<TextView>()
        val isCurrentHistoryTabShown = binding.exportHistoryHost.isVisible

        if ((updateFileList && !isCurrentHistoryTabShown)    //想要更新文件列表tab，并且现在就是文件列表tab。两个都显示文字
            || (!updateFileList && isCurrentHistoryTabShown) //想要更新历史tab，并且现在就是历史tab。两个都显示文字
            ) {
            fileListTabTv?.text = getString(R.string.transfer_list)
            historyTabTv?.text = getString(R.string.export_history)
        } else if (updateFileList) { //想要更新文件列表tab，并且现在不是文件列表tab。文件列表tab显示星号
            fileListTabTv?.text = getString(R.string.transfer_list_2)
            historyTabTv?.text = getString(R.string.export_history)
        } else { //想要更新历史tab，并且现在不是历史tab。文件列表tab显示星号
            fileListTabTv?.text = getString(R.string.transfer_list)
            historyTabTv?.text = getString(R.string.export_history_2)
        }
    }

    private fun initTabs() {
        val transferFileList = binding.tabLayout.newTextTab(getString(R.string.transfer_list), true, 18f)
        transferFileList.view.onClick {
            changeRcvEmptyTextVisible()
            binding.receiveRcv.visible()
            binding.exportHistoryHost.gone()
            receivedFileListTab.customView.asOrNull<TextView>()?.let { tabTv ->
                tabTv.text = getString(R.string.transfer_list)
            }
        }
        receivedFileListTab = transferFileList
        val exportHistory = binding.tabLayout.newTextTab(getString(R.string.export_history), false, 18f)
        exportHistory.view.onClick {
            changeRcvEmptyTextVisible()
            binding.receiveRcv.gone()
            binding.exportHistoryHost.visible()
            exportHistoryTab.customView.asOrNull<TextView>()?.let { tabTv ->
                tabTv.text = getString(R.string.export_history)
            }
        }
        exportHistoryTab = exportHistory
        binding.tabLayout.addTab(transferFileList)
        binding.tabLayout.addTab(exportHistory)
        binding.tabLayout.initSelectedListener()
    }

    fun initRcv() {
        binding.receiveRcv.apply {
            adapter = mAdapter
            layoutManager = LinearLayoutManager(context)
            setHasFixedSize(true)
            addItemDecoration(VertPaddingItemDecoration(0, 0, 2.dp))
        }
    }

    fun changeRcvEmptyTextVisible() {
        binding.apply {
            val isRcvVisible = receiveRcv.isVisible
            if (isRcvVisible) {
                if (mAdapter.datas.isEmpty()) {
                    receiveRcvEmptyTv.visible()
                } else {
                    receiveRcvEmptyTv.gone()
                }
            } else {
                receiveRcvEmptyTv.gone()
            }
        }
    }
}