package com.allan.mydroid.views.send

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.allan.mydroid.R
import com.allan.mydroid.beansinner.ShareInBean
import com.allan.mydroid.databinding.ActivityMyDroidSendlistBinding
import com.allan.mydroid.globals.ShareInUrisObj
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
import com.au.module_android.utils.launchOnThread
import com.au.module_android.utils.logd
import com.au.module_android.utils.logdNoFile
import com.au.module_android.utils.transparentStatusBar
import com.au.module_android.utils.unsafeLazy
import com.au.module_androidui.dialogs.ConfirmBottomSingleDialog
import com.au.module_androidui.dialogs.ConfirmCenterDialog
import com.au.module_androidui.toast.ToastBuilder
import com.au.module_imagecompressed.PickerType
import com.au.module_imagecompressed.compatMultiUriPickerForResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SendListSelectorFragment : BindingFragment<ActivityMyDroidSendlistBinding>() {
    companion object {
        const val KEY_AUTO_ENTER_SEND_VIEW = "key_auto_import"
        const val KEY_START_TYPE = "entry_start_type"
        const val MY_DROID_SHARE_IMPORT_URIS = "myDroidShareReceiverUris"

        fun start(context: Context, autoEnterSendView: Boolean) {
            Globals.finishFragment(SendListSelectorFragment::class.java)
            FragmentShellActivity.start(
                context, SendListSelectorFragment::class.java,
                bundleOf(KEY_AUTO_ENTER_SEND_VIEW to autoEnterSendView)
            )
        }

        fun parseShareImportIntent(fragment: Fragment) {
            val isFromNewShareImportUris = fragment.arguments?.getString(MY_DROID_SHARE_IMPORT_URIS)
            fragment.arguments?.remove(MY_DROID_SHARE_IMPORT_URIS)
            logdNoFile { "parse ShareImport Intent $isFromNewShareImportUris" }
            if (isFromNewShareImportUris == MY_DROID_SHARE_IMPORT_URIS) {
                fragment.lifecycleScope.launch {
                    delay(100)
                    FragmentShellActivity.start(fragment.requireActivity(), SendListSelectorFragment::class.java)
                }
            }
        }
    }

    override fun isPaddingStatusBar() = false

    private val common = object : SendListSelectorCommon(this, true) {
        override fun rcv() = binding.rcv

        override fun empty() = binding.empty

        override fun onHolderClick(bean: ShareInBean?, mode:String) {
            if (mode == "delete" && bean != null) {
                deleteBean(bean)
            }
        }
    }

    private fun deleteBean(bean: ShareInBean) {
        ShareInUrisObj.deleteUris(listOf(bean.uriUuid))
        common.reload()
    }

    private val autoImport by unsafeLazy { arguments?.getBoolean(KEY_AUTO_ENTER_SEND_VIEW) == true }

    val pickerResult = compatMultiUriPickerForResult(9)
    val documentResult = getContentForResult()

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
                        true -> {
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

        if (true) {
            FragmentShellActivity.start(requireActivity(), MyDroidSendFragment::class.java)
        } else {
            val helper = PermissionStorageHelper()
            if(helper.ifGotoMgrAll {
                    ConfirmCenterDialog.show(childFragmentManager,
                        getString(R.string.app_management_permission),
                        getString(R.string.global_permission_prompt),
                        "OK") {
                        helper.gotoMgrAll(requireActivity())
                        it.dismissAllowingStateLoss()
                    }
                }) {
                FragmentShellActivity.start(requireActivity(), MyDroidSendFragment::class.java)

//            permissionResult.safeRun({
//                FragmentShellActivity.start(requireActivity(), MyDroidSendFragment::class.java)
//            }, notGivePermissionBlock = {
//                ToastBuilder().setMessage("请授权媒体权限，否则，无法访问文件。").setIcon("warn").setOnTop().toast()
//            })
            }
        }
    }

    val permissionUtil = NotificationUtil.Companion.createPostNotificationPermissionResult(this)

    override fun onDestroy() {
        super.onDestroy()

        MyDroidKeepLiveService.Companion.stopMyDroidAlive()
    }

    private fun initActionButtons() {
        if (!isPhotoPickerAvailable(requireActivity())) {
            binding.selectImageAndVideoText.gone()
            binding.selectImageAndVideoBtn.gone()
        } else {
            val selectGalleryRun:(view: View)->Unit  = {
                pickerResult.setCurrentMaxItems(9)
                pickerResult.launchByAll(PickerType.IMAGE_AND_VIDEO, null) { uris->
                    logd { "file uri: $uris" }
                    val urisList = ArrayList<Uri>()
                    for (uri in uris) {
                        urisList.add(uri)
                    }
                    onUrisBack(urisList)
                }
            }

            binding.selectImageAndVideoBtn.onClick(selectGalleryRun)
            binding.selectImageAndVideoText.onClick(selectGalleryRun)
        }

        val documentRun:(view: View)->Unit = {
            documentResult.start("*/*") { uris->
                onUrisBack(uris)
            }
        }
        binding.selectDocumentBtn.onClick(documentRun)
        binding.selectDocumentText.onClick(documentRun)
    }

    private fun onUrisBack(uris: List<Uri>) {
        lifecycleScope.launchOnThread {
            ShareInUrisObj.addNewUris(uris)
            common.reload()
        }
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
        common.reload()
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
            if (!common.isEmpty()) {
                jumpIntoMyDroidSend()
            }
        }
    }

}