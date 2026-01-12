package com.au.audiorecordplayer.cam2

import android.Manifest
import android.content.Context
import android.content.res.Configuration
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Looper
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.au.audiorecordplayer.cam2.bean.UiPictureBean
import com.au.audiorecordplayer.cam2.bean.UiRecordBean
import com.au.audiorecordplayer.cam2.bean.UiStateBean
import com.au.audiorecordplayer.cam2.impl.DataRepository
import com.au.audiorecordplayer.cam2.impl.MyCamManager
import com.au.audiorecordplayer.cam2.impl.MyCamViewModel
import com.au.audiorecordplayer.cam2.impl.NeedSizeUtil
import com.au.audiorecordplayer.cam2.view.SurfaceFixSizeUnion
import com.au.audiorecordplayer.cam2.view.cam.PreviewMode
import com.au.audiorecordplayer.cam2.view.gl.CamGLSurfaceView
import com.au.audiorecordplayer.databinding.FragmentCamera2Binding
import com.au.audiorecordplayer.util.FileUtil
import com.au.audiorecordplayer.util.MainUIManager
import com.au.audiorecordplayer.util.MyLog
import com.au.module_android.Globals
import com.au.module_android.click.onClick
import com.au.module_android.log.logdNoFile
import com.au.module_android.simpleflow.StatusState
import com.au.module_android.simpleflow.collectStatusState
import com.au.module_android.utils.ViewVisibilityDebounce
import com.au.module_android.utils.asOrNull
import com.au.module_android.utils.currentStatusBarAndNavBarHeight
import com.au.module_android.utils.dp
import com.au.module_android.utils.getScreenFullSize
import com.au.module_android.utils.gone
import com.au.module_android.utils.immersive
import com.au.module_android.utils.unsafeLazy
import com.au.module_android.utils.visible
import com.au.module_androidui.ui.base.ImmersiveMode
import com.au.module_androidui.ui.bindings.BindingFragment
import com.au.module_simplepermission.createMultiPermissionForResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class Camera2Fragment : BindingFragment<FragmentCamera2Binding>() {
    override fun immersiveMode(): ImmersiveMode {
        return ImmersiveMode.PaddingNavigationBar
    }

    val permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    private val permissionHelper = createMultiPermissionForResult(permissions)

    private val viewModel by unsafeLazy { ViewModelProvider(requireActivity())[MyCamViewModel::class.java] }

    private lateinit var recordBtnDebounce: ViewVisibilityDebounce
    private lateinit var takePicBtnDebounce: ViewVisibilityDebounce
    private lateinit var recordHelper : Camera2FragmentRecord
    private lateinit var settingsHelper : Camera2FragmentSettings

    private var mToastJob : Job? = null
    fun toastOnText(string:String, duration:Long = 3000) {
        val job = mToastJob
        if (job != null) {
            binding.toastInfo.text = binding.toastInfo.text.toString() + "\n" + string
            binding.toastInfo.visible()
            job.cancel()
        } else {
            binding.toastInfo.text = string
            binding.toastInfo.visible()
        }

        mToastJob = lifecycleScope.launch {
            delay(duration)
            binding.toastInfo.text = ""
            binding.toastInfo.gone()
            mToastJob = null
        }
    }

    override fun onBindingCreated(savedInstanceState: Bundle?) {
        //初始化按钮显示防抖
        recordBtnDebounce = ViewVisibilityDebounce(lifecycleScope, binding.recordBtn)
        takePicBtnDebounce = ViewVisibilityDebounce(lifecycleScope, binding.takePicBtn)
        recordHelper = Camera2FragmentRecord(this, binding.timeTv, binding.recordBtn)
        settingsHelper = Camera2FragmentSettings(this)

        //预览的函数体初始化
        binding.previewView.openCameraFunc = {
            openCameraSafety(binding.previewView.surfaceFixSizeUnion)
        }
        binding.previewView.closeCameraFunc = {
            viewModel.camManager.closeCameraDirectly(true)
        }

        initFlows()
        initClicks()
        initLayoutParams()

        initLater()
    }

    private fun initLater() {
        Looper.myQueue().addIdleHandler {
            settingsHelper.initUis()
            false
        }
    }

    private fun initLayoutParams() {
        binding.settingBtn.post {
            requireActivity().currentStatusBarAndNavBarHeight().also { bars->
                binding.settingBtn.layoutParams = (binding.settingBtn.layoutParams as ConstraintLayout.LayoutParams).also {
                    it.topMargin = (bars?.first ?: 32.dp) + 4.dp
                }
            }
        }
    }

    private fun initFlows() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                //2. 收集到了用户的 数据
                viewModel.camManager.uiState.collectStatusState(
                    onSuccess = { bean->
                        MyLog.d("uiState collected! $bean")
                        val picture = bean.pictureTokenBean
                        val record = bean.recordBean
                        val needSwitchToCamIdBean = bean.needSwitchToCamIdBean

                        when (bean.currentMode) {
                            MyCamManager.constStateNone,
                            MyCamManager.constStateDied -> {
                                recordBtnDebounce.invisible()
                                takePicBtnDebounce.invisible()
                            }
//                            MyCamManager.constStatePreview -> {
//                                recordBtnDebounce.visible()
//                                takePicBtnDebounce.invisible()
//                            }
                            MyCamManager.constStatePictureAndRecordAndPreview,
                            MyCamManager.constStatePictureAndPreview -> {
                                recordBtnDebounce.visible()
                                takePicBtnDebounce.visible()
                            }
                        }

                        if (picture != null) {
                            when (picture) {
                                is UiPictureBean.PictureFailed -> {
                                    toastOnText("拍照失败：errorCode${picture.err}")
                                }
                                is UiPictureBean.PictureToken -> {
                                    toastOnText("拍照成功：${picture.path}", 6000)
                                }
                            }
                        } else if (record != null) {
                            when (record) {
                                is UiRecordBean.RecordEnd -> {
                                    toastOnText("视频保存在：${record.path}", 6000)
                                    recordHelper.onRecordEnd()
                                }

                                is UiRecordBean.RecordStart -> {
                                    toastOnText("录制开始...")
                                    if (record.suc) {
                                        recordHelper.onRecordStart()
                                    } else {
                                        recordHelper.onRecordEnd()
                                        toastOnText("录制出现异常")
                                    }
                                }
                                is UiRecordBean.RecordFailed -> {
                                    toastOnText("录制失败：errorCode${record.err}")
                                }
                            }
                        } else if (needSwitchToCamIdBean != null) {
                            toastOnText("切换摄像头...")
                            openCameraSafety(binding.previewView.surfaceFixSizeUnion)
                        }
                    },
                    onError = { exMsg->
                    }
                )
            }
        }

        lifecycleScope.launch {
            MyLog.d("toast collect start...")
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.camManager.toastState.collect {
                    MyLog.d("toast state collected! $it")
                    toastOnText(it.msg)
                }
            }
        }

        lifecycleScope.launch {
            DataRepository.shaderModeFlow.collect { filterType ->
                if (DataRepository.previewMode == PreviewMode.GL_SURFACE_VIEW) {
                    binding.previewView.realView.asOrNull<CamGLSurfaceView>()?.camRenderer?.changeFilter(filterType)
                }
            }
        }
    }

    private fun initClicks() {
        binding.settingBtn.onClick {
            settingsHelper.toggle()
        }

        binding.takePicBtn.onClick {
            viewModel.camManager.takePicture(
                Globals.goodFilesDir.absolutePath + "/pictures", "PIC_" + FileUtil.longTimeToStr(System.currentTimeMillis()) + ".jpg")
        }

        binding.recordBtn.onClick {
            if (!recordHelper.isRecording) {
                viewModel.camManager.startRecord()
            } else {
                viewModel.camManager.stopRecord()
            }
        }
        binding.switchCamBtn.onClick {
            changePreviewNeedSize(requireActivity())

            val currentMode = viewModel.camManager.uiState.value.asOrNull<StatusState.Success<UiStateBean>>()?.data
            when (currentMode?.currentMode) {
                MyCamManager.constStatePictureAndPreview,
//                MyCamManager.constStatePreview
                    -> {
                    viewModel.camManager.switchFontBackCam()
                }
                else -> {
                    toastOnText("当前模式不支持切换")
                }
            }
        }
//        binding.modeTv.onClick {
//            val currentMode = viewModel.camManager.uiState.value.asOrNull<StatusState.Success<UiStateBean>>()?.data
//            when (currentMode?.currentMode) {
//                 MyCamManager.constStatePreview -> {
//                     viewModel.camManager.sendEmptyMessage(TRANSMIT_TO_MODE_PICTURE_PREVIEW)
//                }
//                MyCamManager.constStatePictureAndPreview -> {
//                    viewModel.camManager.sendEmptyMessage(TRANSMIT_TO_MODE_PREVIEW)
//                }
//                else -> {
//                    toastOnText("当前模式不支持切换")
//                }
//            }
//        }
    }

    fun openCameraSafety(surface: SurfaceFixSizeUnion?) {
        surface ?: return
        DataRepository.surface = surface.shownSurface
        changePreviewNeedSize(requireActivity())

        logdNoFile { "open camera safety" }
        permissionHelper.safeRun(notGivePermissionBlock = {
            MainUIManager.get().toastSnackbar(view, "请授予相机和录音权限。")
        }){
            viewModel.camManager.openCamera()
        }
    }

    /**
     * 因为是ViewModel中，不得将ac持有和用在lambda和回调中。
     */
    fun changePreviewNeedSize(ac: FragmentActivity) {
        //第一步：获取新的preview size
        val orientation = ac.resources.configuration.orientation
        val clz = NeedSizeUtil.needSizeFmtClass(DataRepository.previewMode)
        val pair = ac.getScreenFullSize()
        var wishW: Int = pair.first
        var wishH: Int = pair.second
        if (wishW < wishH) {
            val h = wishW
            wishW = wishH
            wishH = h
        }
        MyLog.d("wishSize $wishW*$wishH")
        val systemCameraManager = Globals.app.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val previewNeedSize = NeedSizeUtil
            .getByClz(clz, systemCameraManager, "" + DataRepository.cameraId, wishW, wishH)
            .needSize("<State Preview>")
        MyLog.d("needSize " + previewNeedSize.width + " * " + previewNeedSize.height)

        //第二步：设置宽高比
        val needSize = previewNeedSize
        MyLog.d("onSurfaceCreatedInit previewView ${binding.previewView.width} * ${binding.previewView.height}")
        if (orientation != Configuration.ORIENTATION_LANDSCAPE) {
            binding.previewView.setAspectRatio(needSize.width, needSize.height)
        } else {
            binding.previewView.setAspectRatio(needSize.height, needSize.width)
        }

        toastOnText("previewMode is: " + DataRepository.previewMode)

        requireActivity().immersive(statusBarTextDark = false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.close()
        DataRepository.surface = null
    }
}