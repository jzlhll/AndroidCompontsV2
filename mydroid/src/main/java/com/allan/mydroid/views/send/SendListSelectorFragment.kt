package com.allan.mydroid.views.send

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import com.allan.mydroid.R
import com.allan.mydroid.beansinner.UriRealInfoEx
import com.allan.mydroid.databinding.ActivityMyDroidSendlistBinding
import com.allan.mydroid.globals.KEY_AUTO_ENTER_SEND_VIEW
import com.allan.mydroid.globals.MyDroidConst
import com.allan.mydroid.views.MyDroidKeepLiveService
import com.au.module_android.Globals
import com.au.module_android.click.onClick
import com.au.module_android.permissions.PermissionStorageHelper
import com.au.module_android.permissions.getContentForResult
import com.au.module_android.ui.FragmentShellActivity
import com.au.module_android.ui.ToolbarMenuManager
import com.au.module_android.ui.bindings.BindingFragment
import com.au.module_android.utils.NotificationUtil
import com.au.module_android.utils.asOrNull
import com.au.module_android.utils.gone
import com.au.module_android.utils.isPhotoPickerAvailable
import com.au.module_android.utils.logd
import com.au.module_android.utils.logdNoFile
import com.au.module_android.utils.transparentStatusBar
import com.au.module_android.utils.unsafeLazy
import com.au.module_androidui.dialogs.ConfirmBottomSingleDialog
import com.au.module_androidui.dialogs.ConfirmCenterDialog
import com.au.module_androidui.toast.ToastBuilder
import com.au.module_imagecompressed.MultiPhotoPickerContractResult
import com.au.module_imagecompressed.compatMultiPhotoPickerForResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SendListSelectorFragment : BindingFragment<ActivityMyDroidSendlistBinding>() {
    override fun isPaddingStatusBar() = false

    private val common = object : SendListSelectorCommon(this, false) {
        override fun rcv() = binding.rcv

        override fun empty() = binding.empty

        override fun itemClick(bean: UriRealInfoEx?, mode:String) {
            if (mode == "delete" && bean != null) {
                val data = MyDroidConst.sendUriMap.realValue
                data?.remove(bean.uriUuid)
                MyDroidConst.updateSendUriMap(data)
            }
        }
    }

    private var mAutoNextJob: Job? = null
    private var mDelayCancelDialog: ConfirmBottomSingleDialog? = null
    private var mDelayTime = 3

    //todo 有manageAll，则无需本地
//    private val permissionResult = createStoragePermissionForResult(
//            arrayOf(PermissionStorageHelper.MediaType.AUDIO,
//                PermissionStorageHelper.MediaType.IMAGE,
//                PermissionStorageHelper.MediaType.VIDEO,)
//        )

    private fun dialogContent() : String{
        val fmt = getString(R.string.auto_proceed_fmt)
        return String.format(fmt, mDelayTime)
    }

    private val menuMgr by unsafeLazy {
        ToolbarMenuManager(
            this, binding.toolbar,
            R.menu.menu_next,
            Color.WHITE
        ) { menuItem ->
            when (menuItem.itemId) {
                R.id.next -> {
                    when (common.isEmpty()) {
                        2 -> {
                            ToastBuilder().setOnActivity(requireActivity()).setMessage(
                                getString(R.string.select_files_hint)
                            ).setIcon("info").toast()
                        }

                        1 -> {
                            jumpIntoMyDroidSend()
                        }

                        else -> {
                            ToastBuilder().setOnActivity(requireActivity()).setMessage(
                                getString(R.string.empty_file_prompt)).setIcon("info").toast()
                        }
                    }
                }
            }
        }
    }

    private fun jumpIntoMyDroidSend() {
        mDelayCancelDialog?.dismissAllowingStateLoss()
        mDelayCancelDialog = null

        val helper = PermissionStorageHelper()
        if(helper.ifGotoMgrAll {
            ConfirmCenterDialog.Companion.show(childFragmentManager,
                getString(R.string.app_management_permission),
                getString(R.string.global_permission_prompt),
                "OK") {
                helper.gotoMgrAll(requireActivity())
                it.dismissAllowingStateLoss()
            }
        }) {
            FragmentShellActivity.Companion.start(requireActivity(), MyDroidSendFragment::class.java)

//            permissionResult.safeRun({
//                FragmentShellActivity.start(requireActivity(), MyDroidSendFragment::class.java)
//            }, notGivePermissionBlock = {
//                ToastBuilder().setMessage("请授权媒体权限，否则，无法访问文件。").setIcon("warn").setOnTop().toast()
//            })
        }
    }

    val permissionUtil = NotificationUtil.Companion.createPostNotificationPermissionResult(this)

    override fun onDestroy() {
        super.onDestroy()

        MyDroidKeepLiveService.Companion.stopMyDroidAlive()
    }

    private val autoImport by unsafeLazy { arguments?.getBoolean(KEY_AUTO_ENTER_SEND_VIEW) == true }

    val pickerResult = compatMultiPhotoPickerForResult(9)
    val documentResult = getContentForResult()

    private fun initActionButtons() {
        if (!isPhotoPickerAvailable(requireActivity())) {
            binding.selectImageAndVideoText.gone()
            binding.selectImageAndVideoBtn.gone()
        } else {
            val selectGalleryRun:(view: View)->Unit  = {
                pickerResult.setCurrentMaxItems(9)
                pickerResult.launchOneByOne(MultiPhotoPickerContractResult.PickerType.IMAGE_AND_VIDEO, null) {uri->
                    logd { "file uri: $uri" }
                }
            }

            binding.selectImageAndVideoBtn.onClick(selectGalleryRun)
            binding.selectImageAndVideoText.onClick(selectGalleryRun)
        }

        val documentRun:(view: View)->Unit = {
            documentResult.start("*/*") { uris->
                uris.forEach {uri->
                    logdNoFile { "get documents $uri" }
                }
            }
        }
        binding.selectDocumentBtn.onClick(documentRun)
        binding.selectDocumentText.onClick(documentRun)
    }

    override fun onBindingCreated(savedInstanceState: Bundle?) {
        NotificationUtil.Companion.requestPermission(permissionUtil) {
            MyDroidKeepLiveService.Companion.keepMyDroidAlive()
        }

        initActionButtons()

        binding.adHost.setColor(Globals.getColor(com.au.module_androidcolor.R.color.color_normal_block0))
        binding.adHost.startAnimation()

        binding.toolbar.setNavigationOnClickListener {
            requireActivity().finishAfterTransition()
        }

        binding.infoText.onClick {
            ConfirmBottomSingleDialog.Companion.show(childFragmentManager,
                getString(R.string.disclaimer_title),
                    getString(R.string.disclaimer_content),
                getString(R.string.action_confirm)) {
                it.dismissAllowingStateLoss()
            }
        }

        menuMgr.showMenu()

        requireActivity().transparentStatusBar(statusBarTextDark = false) { insets, statusBarsHeight, navigationBarHeight ->
            binding.toolbar.layoutParams.asOrNull<ConstraintLayout.LayoutParams>()?.let { toolbarLP->
                toolbarLP.topMargin = statusBarsHeight
                binding.toolbar.layoutParams = toolbarLP
            }
            insets
        }

        common.onCreated()
        if (autoImport) autoImportAction()
    }

    override fun onStart() {
        super.onStart()
        common.onStart()
    }

    private fun autoImportAction() {
        mDelayCancelDialog = ConfirmBottomSingleDialog.Companion.show(childFragmentManager,
            getString(R.string.action_auto_next),
            dialogContent(),
            getString(com.au.module_android.R.string.cancel),
        ) {
            mAutoNextJob?.cancel()
            it.dismissAllowingStateLoss()
        }

        mAutoNextJob = lifecycleScope.launch {
            while(mDelayTime-- > 0) {
                delay(1000)
                mDelayCancelDialog?.changeContent(dialogContent())
            }
            //自动跳入
            if (common.isEmpty() == 1) {
                jumpIntoMyDroidSend()
            }
        }
    }

}