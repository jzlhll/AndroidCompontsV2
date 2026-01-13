package com.allan.androidlearning.pictureselector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.au.module_android.log.logdNoFile
import com.au.module_imagecompressed.compressor.CompressCacheConstManager
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.luck.picture.lib.R
import com.luck.picture.lib.config.PictureMimeType
import com.luck.picture.lib.engine.CropFileEngine
import com.luck.picture.lib.entity.LocalMedia
import com.luck.picture.lib.interfaces.OnMediaEditInterceptListener
import com.luck.picture.lib.style.PictureSelectorStyle
import com.luck.picture.lib.utils.DateUtils
import com.luck.picture.lib.utils.StyleUtils
import com.yalantis.ucrop.UCrop
import com.yalantis.ucrop.UCropImageEngine
import java.io.File

class UcropHelp(val mContext: Context) {

    fun cropFileEngine() : ImageFileCropEngine = ImageFileCropEngine()
    fun customEditMediaEvent() : MeOnMediaEditInterceptListener = MeOnMediaEditInterceptListener(
        cacheDir, buildOptions())

    private val cacheDir:String
        get() = CompressCacheConstManager.cacheDir.absolutePath

    private val selectorStyle = PictureSelectorStyle()

    private val notSupportCrop: Array<String>
        get() = arrayOf(PictureMimeType.ofGIF(), PictureMimeType.ofWEBP()) //emptyArrayOf()

    private fun buildOptions(): UCrop.Options {
        val options = UCrop.Options()
        //options.setHideBottomControls(!cb_hide.isChecked());
        //options.setFreeStyleCropEnabled(cb_styleCrop.isChecked());
        //options.setShowCropFrame(cb_showCropFrame.isChecked());
        //options.setShowCropGrid(cb_showCropGrid.isChecked());
        //options.setCircleDimmedLayer(cb_crop_circular.isChecked());
        //options.withAspectRatio(aspect_ratio_x, aspect_ratio_y);
        options.setCropOutputPathDir(cacheDir)
        options.isCropDragSmoothToCenter(false)
        options.setSkipCropMimeType(*notSupportCrop)
        options.isForbidCropGifWebp(true)
        options.isForbidSkipMultipleCrop(true)
        options.setMaxScaleMultiplier(100f)
        if (selectorStyle.selectMainStyle.statusBarColor != 0) {
            val mainStyle = selectorStyle.selectMainStyle
            val statusBarColor = mainStyle.statusBarColor
            options.setExtraStatusBarTextIsDark(false)
            if (StyleUtils.checkStyleValidity(statusBarColor)) {
                options.setStatusBarColor(statusBarColor)
                options.setToolbarColor(statusBarColor)
            } else {
                options.setStatusBarColor(ContextCompat.getColor(mContext, R.color.ps_color_grey))
                options.setToolbarColor(ContextCompat.getColor(mContext, R.color.ps_color_grey))
            }
            val titleBarStyle = selectorStyle.titleBarStyle
            if (StyleUtils.checkStyleValidity(titleBarStyle.titleTextColor)) {
                options.setToolbarWidgetColor(titleBarStyle.titleTextColor)
            } else {
                options.setToolbarWidgetColor(ContextCompat.getColor(mContext, R.color.ps_color_white))
            }
        } else {
            options.setStatusBarColor(ContextCompat.getColor(mContext, R.color.ps_color_grey))
            options.setToolbarColor(ContextCompat.getColor(mContext, R.color.ps_color_grey))
            options.setToolbarWidgetColor(ContextCompat.getColor(mContext, R.color.ps_color_white))
        }
        return options
    }

    /**
     * 自定义裁剪
     */
    inner class ImageFileCropEngine : CropFileEngine {

        override fun onStartCrop(fragment: Fragment, srcUri: Uri, destinationUri: Uri, dataSource: ArrayList<String?>, requestCode: Int) {
            val options = buildOptions()
            val uCrop = UCrop.of(srcUri, destinationUri, dataSource)
            uCrop.withOptions(options)
            uCrop.setImageEngine(object : UCropImageEngine {
                override fun loadImage(context: Context, url: String?, imageView: ImageView) {
                    if (!ImageLoaderUtils.assertValidRequest(context)) {
                        return
                    }
                    Glide.with(context).load(url).override(180, 180).into(imageView)
                }

                override fun loadImage(context: Context, url: Uri?, maxWidth: Int, maxHeight: Int, call: UCropImageEngine.OnCallbackListener<Bitmap?>?) {
                    Glide.with(context).asBitmap().load(url).override(maxWidth, maxHeight).into(object : CustomTarget<Bitmap?>() {
                        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap?>?) {
                            call?.onCall(resource)
                        }

                        override fun onLoadCleared(placeholder: Drawable?) {
                            call?.onCall(null)
                        }
                    })
                }
            })
            uCrop.start(fragment.requireActivity(), fragment, requestCode)
        }
    }

    class MeOnMediaEditInterceptListener(private val outputCropPath: String?, private val options: UCrop.Options) : OnMediaEditInterceptListener {
        override fun onStartMediaEdit(fragment: Fragment, currentLocalMedia: LocalMedia, requestCode: Int) {
            val currentEditPath = currentLocalMedia.getAvailablePath()
            val inputUri = if (PictureMimeType.isContent(currentEditPath))
                Uri.parse(currentEditPath)
            else
                Uri.fromFile(File(currentEditPath))
            val destFile = File(outputCropPath, DateUtils.getCreateFileName("CROP_") + ".jpeg")
            val destinationUri = Uri.fromFile(destFile)
            logdNoFile { "destFile $destFile" }
            val uCrop = UCrop.of<Any?>(inputUri, destinationUri)
            options.setHideBottomControls(false)
            uCrop.withOptions(options)
            uCrop.setImageEngine(object : UCropImageEngine {
                override fun loadImage(context: Context, url: String?, imageView: ImageView) {
                    logdNoFile { "url1 $url" }
                    if (!ImageLoaderUtils.assertValidRequest(context)) {
                        return
                    }
                    Glide.with(context).load(url).override(180, 180).into(imageView)
                }

                override fun loadImage(context: Context, url: Uri?, maxWidth: Int, maxHeight: Int, call: UCropImageEngine.OnCallbackListener<Bitmap?>?) {
                    logdNoFile { "url2 $url" }
                    Glide.with(context).asBitmap().load(url).override(maxWidth, maxHeight).into(object : CustomTarget<Bitmap?>() {
                        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap?>?) {
                            call?.onCall(resource)
                        }

                        override fun onLoadCleared(placeholder: Drawable?) {
                            call?.onCall(null)
                        }
                    })
                }
            })
            uCrop.startEdit(fragment.requireActivity(), fragment, requestCode)
        }
    }
}
