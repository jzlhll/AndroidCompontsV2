package com.allan.mydroid.views.receiver

import android.os.Bundle
import android.widget.TextView
import androidx.core.os.bundleOf
import com.allan.mydroid.R
import com.allan.mydroid.databinding.FragmentMyDroidReceiveListBinding
import com.allan.mydroid.globals.KEY_AUTO_ENTER_SEND_VIEW
import com.allan.mydroid.globals.MyDroidConst
import com.allan.mydroid.views.send.SendListSelectorFragment
import com.au.module_android.Globals
import com.au.module_android.click.onClick
import com.au.module_android.ui.FragmentShellActivity
import com.au.module_android.ui.bindings.BindingFragment
import com.au.module_android.ui.views.ToolbarInfo
import com.au.module_android.utils.asOrNull
import com.au.module_android.utils.gone
import com.au.module_android.utils.launchOnThread
import com.au.module_android.utils.launchOnUi
import com.au.module_android.utils.unsafeLazy
import com.au.module_android.utils.visible
import com.au.module_androidui.toast.ToastBuilder
import com.google.android.material.tabs.TabLayout
import kotlin.getValue

class ReceiveFromH5FileListFragment : BindingFragment<FragmentMyDroidReceiveListBinding>() {
    lateinit var receivedFileListTab: TabLayout.Tab
    lateinit var exportHistoryTab: TabLayout.Tab

    private val mFileListMgr by unsafeLazy { ReceiveFromH5FileListManager(this) }

    private val isActivityMode by unsafeLazy { arguments?.getBoolean("isActivityMode") ?: false }

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

    val fileExportFailCallback:(String)->Unit = { info->
        Globals.mainScope.launchOnUi {
            ToastBuilder().setOnTop().setMessage(info).setIcon("error").toast()
        }
    }

    val fileExportSuccessCallback:(info:String, exportFileStr:String)->Unit = { info, exportFileStr->
        Globals.mainScope.launchOnUi {
            ToastBuilder().setOnTop().setMessage(info.replace("/storage/emulated/0/", "/sdcard/"))
                .setIcon("success").toast()

            //确保写错。避免退出界面，没写。
            mFileListMgr.writeHistory(exportFileStr) {
                fileChanged()
                mFileListMgr.loadHistory(false)
            }
        }
    }

    val fileChanged:()->Unit = {
        Globals.mainScope.launchOnThread {
            mFileListMgr.loadFileList()
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
            mFileListMgr.loadFileList()
        }

        initLater()
    }

    private fun initLater() {
        Globals.mainHandler.post {
            val host = binding

            val transferFileList = host.tabLayout.newTextTab(getString(R.string.transfer_list), true, 18f)
            transferFileList.view.onClick {
                host.receiveRcv.visible()
                mFileListMgr.changeRcvEmptyTextVisible()
                host.exportHistoryHost.gone()
                receivedFileListTab.customView.asOrNull<TextView>()?.let { tabTv->
                    tabTv.text = getString(R.string.transfer_list)
                }
            }
            receivedFileListTab = transferFileList
            val exportHistory = host.tabLayout.newTextTab(getString(R.string.export_history), false, 18f)
            exportHistory.view.onClick {
                host.receiveRcv.gone()
                mFileListMgr.changeRcvEmptyTextVisible()
                host.exportHistoryHost.visible()
                exportHistoryTab.customView.asOrNull<TextView>()?.let { tabTv->
                    tabTv.text = getString(R.string.export_history)
                }
            }
            exportHistoryTab = exportHistory

            host.tabLayout.addTab(transferFileList)
            host.tabLayout.addTab(exportHistory)
            host.tabLayout.initSelectedListener()

            mFileListMgr.initRcv()
            mFileListMgr.loadFileList()
            mFileListMgr.loadHistory(true)
        }
    }
}