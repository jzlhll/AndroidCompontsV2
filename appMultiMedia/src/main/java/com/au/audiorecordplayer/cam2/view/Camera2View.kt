package com.au.audiorecordplayer.cam2.view

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.widget.FrameLayout
import com.au.audiorecordplayer.cam2.impl.DataRepository
import com.au.audiorecordplayer.cam2.view.gl.CamGLSurfaceView
import com.au.audiorecordplayer.cam2.view.cam.CamSurfaceView
import com.au.audiorecordplayer.cam2.view.cam.CamTextureView
import com.au.audiorecordplayer.cam2.view.cam.PreviewMode
import com.au.module_android.utils.logdNoFile
import kotlin.math.roundToInt

class Camera2View : FrameLayout, Camera2ViewBase{
    companion object {
        const val TAG = "Cam2PreviewView"
    }

    private var mIsInit = false

    var surfaceFixSizeUnion: SurfaceFixSizeUnion? = null

    ///////////////////参数设定
    /**
     * 创建相机函数
     */
    override lateinit var openCameraFunc:()->Unit
    /**
     * 关闭相机函数
     */
    override lateinit var closeCameraFunc:()->Unit

    ///////////////////

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        addRealView()
    }

    private fun addRealView() {
        if (mIsInit) return
        if (isInEditMode) return

        val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        logdNoFile { "add real view--& set callback" }
        val camView = when (DataRepository.previewMode) {
            PreviewMode.SURFACE_VIEW -> {
                CamSurfaceView(context).also {
                    it.setCallback(object : IViewOnSurfaceCallback {
                        override fun onSurfaceCreated(surfaceHolderOrSurfaceTexture: Any) {
                            val sh = surfaceHolderOrSurfaceTexture as SurfaceHolder
                            surfaceFixSizeUnion = SurfaceFixSizeUnion(surfaceHolder = sh, shownSurface = sh.surface)
                            openCameraFunc()
                        }

                        override fun onSurfaceDestroyed() {
                            closeCameraFunc()
                        }

                        override fun onSurfaceChanged() {
                        }
                    })
                }
            }
            PreviewMode.TEXTURE_VIEW -> {
                CamTextureView(context).also {
                    it.setCallback(object : IViewOnSurfaceCallback {
                        override fun onSurfaceCreated(surfaceHolderOrSurfaceTexture: Any) {
                            val st = surfaceHolderOrSurfaceTexture as SurfaceTexture
                            val surface = Surface(st)
                            surfaceFixSizeUnion = SurfaceFixSizeUnion(surfaceTexture = st, shownSurface = surface)
                            openCameraFunc()
                        }

                        override fun onSurfaceDestroyed() {
                            closeCameraFunc()
                        }

                        override fun onSurfaceChanged() {
                        }
                    })
                }
            }
            PreviewMode.GL_SURFACE_VIEW -> {
                CamGLSurfaceView(context).also {
                    it.setCallback(object : IViewOnSurfaceCallback {
                        override fun onSurfaceCreated(surfaceHolderOrSurfaceTexture: Any) {
                            val st = surfaceHolderOrSurfaceTexture as SurfaceTexture
                            val surface = Surface(st)
                            surfaceFixSizeUnion = SurfaceFixSizeUnion(surfaceTexture = st, shownSurface = surface)

                            post {
                                if (isAttachedToWindow) {
                                    openCameraFunc()
                                }
                            }
                        }

                        override fun onSurfaceDestroyed() {
                            closeCameraFunc()
                        }

                        override fun onSurfaceChanged() {
                        }
                    })
                }
            }
        }

        camView.layoutParams = lp
        addView(camView)
        //调试追加操作界面
        if(false) addView(DrawFrameLayout(context))
        mIsInit = true
    }

    private var aspectRatio = 0f

    /**
     * Sets the aspect ratio for this view. The size of the view will be
     * measured based on the ratio calculated from the parameters.
     *
     * @param width  Camera resolution horizontal size
     * @param height Camera resolution vertical size
     */
    fun setAspectRatio(width: Int, height: Int) {
        Log.d(TAG, "setAspect Ratio : $width x $height")
        require(width > 0 && height > 0) { "Size cannot be negative" }
        aspectRatio = width.toFloat() / height.toFloat()

        surfaceFixSizeUnion?.surfaceHolder?.setFixedSize(width, height)
        surfaceFixSizeUnion?.surfaceTexture?.setDefaultBufferSize(width, height)

        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)

        Log.d(TAG, "Measured width height: $width x $height")

        if (aspectRatio == 0f) {
            setMeasuredDimension(width, height)
        } else {
            // Performs center-crop transformation of the camera frames
            val newWidth: Int
            val newHeight: Int
            val actualRatio = if (width > height) aspectRatio else 1f / aspectRatio
            if (width < height * actualRatio) {
                newHeight = height
                newWidth = (height * actualRatio).roundToInt()
            } else {
                newWidth = width
                newHeight = (width / actualRatio).roundToInt()
            }

            Log.d(TAG, "Measured dimensions set: $newWidth x $newHeight")
            setMeasuredDimension(newWidth, newHeight)
        }
    }

}