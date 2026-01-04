package com.allan.androidlearning.activities2

import android.os.Bundle
import com.allan.androidlearning.databinding.FragmentPictureSelectorBinding
import com.allan.classnameanno.EntryFrgName
import com.au.module_android.click.onClick
import com.au.module_android.log.logd
import com.au.module_androidui.ui.bindings.BindingFragment
import com.luck.picture.lib.basic.PictureSelector
import com.luck.picture.lib.config.SelectMimeType
import com.luck.picture.lib.entity.LocalMedia
import com.luck.picture.lib.interfaces.OnResultCallbackListener
import java.util.ArrayList

@EntryFrgName
class PictureSelectorFragment : BindingFragment<FragmentPictureSelectorBinding>() {
    override fun onBindingCreated(savedInstanceState: Bundle?) {
        binding.btnSelectAudio.onClick {
            PictureSelector.create(this)
                .openGallery(SelectMimeType.ofAudio())
                .setImageEngine(GlideEngine.createGlideEngine())
                .forResult(object : OnResultCallbackListener<LocalMedia> {
                    override fun onResult(result: ArrayList<LocalMedia>?) {
                        logd { "audio result: $result" }
                    }

                    override fun onCancel() {
                        logd { "audio cancel" }
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
