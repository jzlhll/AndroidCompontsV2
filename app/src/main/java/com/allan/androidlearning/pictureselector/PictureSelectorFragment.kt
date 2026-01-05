package com.allan.androidlearning.pictureselector

import android.os.Bundle
import com.allan.androidlearning.R
import com.allan.androidlearning.databinding.FragmentPictureSelectorBinding
import com.allan.classnameanno.EntryFrgName
import com.au.module_android.click.onClick
import com.au.module_android.log.logd
import com.au.module_androidui.ui.bindings.BindingFragment
import com.luck.picture.lib.basic.PictureSelector
import com.luck.picture.lib.config.InjectResourceSource
import com.luck.picture.lib.config.SelectMimeType
import com.luck.picture.lib.entity.LocalMedia
import com.luck.picture.lib.interfaces.OnResultCallbackListener
import com.luck.picture.lib.style.PictureSelectorStyle
import com.luck.picture.lib.style.TitleBarStyle
import java.util.ArrayList

@EntryFrgName
class PictureSelectorFragment : BindingFragment<FragmentPictureSelectorBinding>() {
    override fun onBindingCreated(savedInstanceState: Bundle?) {
        binding.btnSelectAudio.onClick {
            PictureSelector.create(this)
                .openGallery(SelectMimeType.ofAudio())
                .forResult(object : OnResultCallbackListener<LocalMedia> {
                    override fun onResult(result: ArrayList<LocalMedia>?) {
                        logd { "audio result: $result" }
                    }

                    override fun onCancel() {
                        logd { "audio cancel" }
                    }
                })
        }

        binding.btnSelectCustomImage.onClick {
            PictureSelector.create(this)
                .openGallery(SelectMimeType.ofImage())
                .setImageEngine(GlideEngine.createGlideEngine())
                .setInjectLayoutResourceListener { _, resourceSource ->
                    if (resourceSource == InjectResourceSource.MAIN_SELECTOR_LAYOUT_RESOURCE) {
                        R.layout.ps_custom_fragment_selector
                    } else {
                        0
                    }
                }
                .forResult(object : OnResultCallbackListener<LocalMedia> {
                    override fun onResult(result: ArrayList<LocalMedia>?) {
                        logd { "image result: $result" }
                    }

                    override fun onCancel() {
                        logd { "image cancel" }
                    }
                })
        }

        binding.btnSelectImage.onClick {
            PictureSelector.create(this)
                .openGallery(SelectMimeType.ofImage())
                .setImageEngine(GlideEngine.createGlideEngine())
                .forResult(object : OnResultCallbackListener<LocalMedia> {
                    override fun onResult(result: ArrayList<LocalMedia>?) {
                        logd { "image result: $result" }
                    }

                    override fun onCancel() {
                        logd { "image cancel" }
                    }
                })
        }

        binding.btnSelectSystemImage.onClick {
            PictureSelector.create(this)
                .openSystemGallery(SelectMimeType.ofImage())
                .forSystemResult(object : OnResultCallbackListener<LocalMedia> {
                    override fun onResult(result: ArrayList<LocalMedia>?) {
                        logd { "image result: $result" }
                    }

                    override fun onCancel() {
                        logd { "image cancel" }
                    }
                })
        }

        binding.btnSelectImageAndVideo.onClick {
            PictureSelector.create(this)
                .openGallery(SelectMimeType.ofAll())
                .setImageEngine(GlideEngine.createGlideEngine())
                .forResult(object : OnResultCallbackListener<LocalMedia> {
                    override fun onResult(result: ArrayList<LocalMedia>?) {
                        logd { "all result: $result" }
                    }

                    override fun onCancel() {
                        logd { "all cancel" }
                    }
                })
        }
    }
}