package com.au.audiorecordplayer.camgl

import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.au.module_android.ui.views.ViewFragment

class CameraGLFragment : ViewFragment() {
    private var mCameraV2GLSurfaceView: CameraV2GLSurfaceView? = null
    private var mCamera: CameraV2? = null

    override fun onUiCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val activity = requireActivity()
        val glSurfaceView = CameraV2GLSurfaceView(activity)
        mCameraV2GLSurfaceView = glSurfaceView
        val dm = DisplayMetrics()
        activity.display.getMetrics(dm)
        val cam = CameraV2(requireActivity())
        mCamera = cam
        cam.setupCamera(dm.widthPixels, dm.heightPixels)
        if (!cam.openCamera()) {
            return LinearLayout(activity)
        }
        glSurfaceView.init(cam, false)
        return glSurfaceView
    }

    override fun isPaddingStatusBar(): Boolean {
        return false
    }
}