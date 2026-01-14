package com.allan.androidlearning.pictureselector

import android.os.Bundle
import com.allan.androidlearning.databinding.FragmentPictureSelectorBinding
import com.allan.classnameanno.EntryFrgName
import com.au.module_android.click.onClick
import com.au.module_android.log.logdNoFile
import com.au.module_androidui.ui.bindings.BindingFragment
import com.au.module_imagecompressed.compressor.CompressCacheConstManager
import com.luck.picture.lib.basic.PictureSelector
import com.luck.picture.lib.config.SelectMimeType
import com.luck.picture.lib.entity.LocalMedia
import com.luck.picture.lib.interfaces.OnResultCallbackListener
import java.util.ArrayList

@EntryFrgName
class PictureSelectorTestFragment : BindingFragment<FragmentPictureSelectorBinding>() {
    private val ucropHelp by lazy { UcropHelp(requireActivity()) }

    override fun onBindingCreated(savedInstanceState: Bundle?) {
        binding.btnSelectAudio.onClick {
            PictureSelector.create(this)
                .openGallery(SelectMimeType.ofAudio())
                .forResult(object : OnResultCallbackListener<LocalMedia> {
                    override fun onResult(result: ArrayList<LocalMedia>?) {
                        logdNoFile { "image result: ${result?.size}" }
                        result?.forEach { m->
                            logdNoFile { m.toLog() }
                        }
                    }

                    override fun onCancel() {
                        logdNoFile { "image cancel" }
                    }
                })
        }

        binding.btnSelectImage.onClick {
            PictureSelector.create(this)
                .openGallery(SelectMimeType.ofImage())
                .setImageEngine(GlideEngine.createGlideEngine())
                .setMaxSelectNum(Int.MAX_VALUE)
                .forResult(object : OnResultCallbackListener<LocalMedia> {
                    override fun onResult(result: ArrayList<LocalMedia>?) {
                        logdNoFile { "image result: ${result?.size}" }
                        result?.forEach { m->
                            logdNoFile { m.toLog() }
                        }
                    }

                    override fun onCancel() {
                        logdNoFile { "image cancel" }
                    }
                })
        }

        binding.selectUCropImage.onClick {
            PictureSelector.create(this)
                .openGallery(SelectMimeType.ofImage())
                .setImageEngine(GlideEngine.createGlideEngine())
//                .setCropEngine(ucropHelp.cropFileEngine())
                .setEditMediaInterceptListener(ucropHelp.customEditMediaEvent())
                .forResult(object : OnResultCallbackListener<LocalMedia> {
                    override fun onResult(result: ArrayList<LocalMedia>?) {
                        logdNoFile { "image result: ${result?.size}" }
                        result?.forEach { m->
                            logdNoFile { m.toLog() }
                        }
                    }

                    override fun onCancel() {
                        logdNoFile { "image cancel" }
                    }
                })
        }

        binding.selectCompressImage.onClick {
            PictureSelector.create(this)
                .openGallery(SelectMimeType.ofImage())
                .setImageEngine(GlideEngine.createGlideEngine())
                .setCompressEngine(MeCompressEngine())
//                .setCropEngine(ucropHelp.cropFileEngine())
//                .setEditMediaInterceptListener(ucropHelp.customEditMediaEvent())
                .forResult(object : OnResultCallbackListener<LocalMedia> {
                    override fun onResult(result: ArrayList<LocalMedia>?) {
                        logdNoFile { "image result: ${result?.size}" }
                        result?.forEach { m->
                            logdNoFile { m.toLog() }
                        }
                    }

                    override fun onCancel() {
                        logdNoFile { "image cancel" }
                    }
                })
        }

        binding.btnSelectImageAndVideo.onClick {
            PictureSelector.create(this)
                .openGallery(SelectMimeType.ofAll())
                .setVideoPlayerEngine(ExoPlayerEngine())
                .setImageEngine(GlideEngine.createGlideEngine())
                .forResult(object : OnResultCallbackListener<LocalMedia> {
                    override fun onResult(result: ArrayList<LocalMedia>?) {
                        logdNoFile { "image&video result: ${result?.size}" }
                        result?.forEach { m->
                            logdNoFile { m.toLog() }
                        }
                    }

                    override fun onCancel() {
                        logdNoFile { "image&video cancel" }
                    }
                })
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        CompressCacheConstManager.cleanSpace(true)
    }
}