package com.au.audiorecordplayer.cam2.impl

import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.os.Build
import android.view.Surface
import androidx.annotation.RequiresApi
import com.au.audiorecordplayer.util.MyLog
import com.au.module_android.utils.HandlerExecutor
import com.au.module_android.log.logdNoFile
import java.util.concurrent.Executor


/**
 * 抽象类，用于描述，不同的camera session状态
 * 这个类的子类都将是处于camera open之后的状态（StateDied除外）
 */
abstract class AbstractStateBase protected constructor(protected var cameraManager: MyCamManager) {
    protected var mStateBaseCb: IStateBaseCallback? = null

    protected var camSession: CameraCaptureSession? = null

    open fun closeSession() {
        MyLog.d("close session")
        if (camSession == null) {
            MyLog.d("$javaClass no camera cam session")
        }
        camSession?.close()
        camSession = null
        mStateBaseCb = null
    }

    /**
     * 子类必须实现，而不应该调用
     * 创建一个监听完成session的回调信息，并将StateBaseCb外部监听处理
     */
    protected abstract fun s1_createCaptureSessionStateCallback(cameraDevice: CameraDevice): CameraCaptureSession.StateCallback

    protected fun s2_camCaptureSessionSetRepeatingRequest(cameraDevice:CameraDevice, cameraCaptureSession: CameraCaptureSession) {
        try {
            cameraCaptureSession.setRepeatingRequest(
                createCaptureBuilder(cameraDevice).build(),
                null, cameraManager
            )
        } catch (e: CameraAccessException) {
            MyLog.ex(e)
        }
    }

    /**
     * 在createCameraCaptureSessionStateCallback的回调onConfigured中调用实现
     */
    protected abstract fun createCaptureBuilder(cameraDevice: CameraDevice): CaptureRequest.Builder

    abstract fun allIncludePictureSurfaces() : List<Surface>

    /**
     * 该方法用于camera opened以后，创建preview、picture和record等的会话
     * 且session只有一个
     */
    open fun createSession(cb: IStateBaseCallback?): Boolean {
        mStateBaseCb = cb

        val cameraDevice = cameraManager.cameraDevice
        if (cameraDevice != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    createSessionOld(cameraDevice)
                } else {
                    createSessionOld(cameraDevice)
                }
            } catch (e: Exception) {
                MyLog.ex(e)
                return false
            }
        }
        return true
    }

    private fun createSessionOld(cameraDevice: CameraDevice) {
        logdNoFile { "read all surfaces!" }
        cameraDevice.createCaptureSession(
            allIncludePictureSurfaces(),
            s1_createCaptureSessionStateCallback(cameraDevice), cameraManager
        )
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun createSessionNew(cameraDevice: CameraDevice) {
        // 1. 创建 OutputConfiguration 列表
        val outputConfigurations = ArrayList<OutputConfiguration>()
        for (surface in allIncludePictureSurfaces()) { // surfaces 是你的目标 Surface 列表（预览、录像等）
            val config = OutputConfiguration(surface)
            outputConfigurations.add(config)
        }

        // 2. 准备一个 Executor（不建议使用主线程执行器:cite[1]）
        // 你可以使用 Context.getMainExecutor() 获取主线程执行器，但官方建议使用后台线程:cite[1]。
        // 通常使用一个专门的相机线程的 Executor。如果你之前有 mCameraHandler，可以这样包装：
        val cameraExecutor: Executor = HandlerExecutor(cameraManager)

        // 3. 创建 StateCallback
        val sessionCallback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                camSession = cameraCaptureSession
                s2_camCaptureSessionSetRepeatingRequest(cameraDevice, cameraCaptureSession)

                if (mStateBaseCb != null) {
                    val cb = mStateBaseCb as IStatePreviewCallback
                    cb.onPreviewSucceeded()
                }
            }

            override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                MyLog.e("Error Configure Preview!")
                if (mStateBaseCb != null) {
                    val cb = mStateBaseCb as IStatePreviewCallback
                    cb.onPreviewFailed()
                }
            }
        }

// 4. 构建 SessionConfiguration 对象
        val sessionConfiguration = SessionConfiguration(
            SessionConfiguration.SESSION_HIGH_SPEED,  // 常规会话类型。高速拍摄可使用 SESSION_HIGH_SPEED
            outputConfigurations,
            cameraExecutor,  // 传入 Executor
            sessionCallback
        )

        // 7. 创建捕获会话
        try {
            cameraDevice.createCaptureSession(sessionConfiguration)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

}
