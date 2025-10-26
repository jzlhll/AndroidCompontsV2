package com.au.audiorecordplayer.cam2.view

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import com.au.module_android.utils.asOrNull
import kotlin.math.roundToInt


class Cam2PreviewView : FrameLayout {
    enum class PreviewMode {
        SURFACE_VIEW,
        TEXTURE_VIEW,
        GL_SURFACE_VIEW
    }

    companion object {
        /**
         * 暂时采用静态变量来标记；可以改成attr。懒得做了。
         */
        var previewMode = PreviewMode.SURFACE_VIEW
        const val TAG = "Cam2PreviewView"
    }

    private var mIsInit = false
    private var mCallback: IViewStatusChangeCallback? = null
    private var mRealView: View? = null
    val realView: View?
        get() = mRealView

    fun setCallback(mCallback: IViewStatusChangeCallback?) {
        this.mCallback = mCallback
    }

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (mIsInit) return
        if (isInEditMode) return

        val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        when (previewMode) {
            PreviewMode.SURFACE_VIEW -> {
                addView(CamSurfaceView(context).also {
                    mRealView = it
                    it.layoutParams = lp
                    it.setCallback(mCallback)
                })
            }
            PreviewMode.TEXTURE_VIEW -> {
                addView(CamTextureView(context).also {
                    mRealView = it
                    it.layoutParams = lp
                    it.setCallback(mCallback)
                })
            }
            else -> {
                //todo
            }
        }

        addView(DrawFrameLayout(context))

        mIsInit = true
    }

    private var mSurface: Surface? = null

    val surface: Surface
        get() {
            val surface = mSurface
            if (surface != null) {
                return surface
            }

            val v = mRealView
            val newSurface = if (v is TextureView) {
                Surface(v.surfaceTexture)
            } else {
                (v as SurfaceView).holder.surface
            }
            mSurface = newSurface
            return newSurface
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
        Log.d(TAG, "setAspectRatio : $width x $height")
        require(width > 0 && height > 0) { "Size cannot be negative" }
        aspectRatio = width.toFloat() / height.toFloat()

        realView?.asOrNull<SurfaceView>()?.holder?.setFixedSize(width, height)
        realView?.asOrNull<TextureView>()?.surfaceTexture?.setDefaultBufferSize(width, height)
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